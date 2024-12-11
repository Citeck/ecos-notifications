package ru.citeck.ecos.notifications.domain.reminder.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailOperator
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_ATT_CERTIFICATES
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_ATT_DEFERRED_BULK_MAILS
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_ATT_REMINDER_TYPE
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_SOURCE_ID
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_TYPE_ID
import ru.citeck.ecos.notifications.domain.reminder.dto.Reminder
import ru.citeck.ecos.notifications.domain.reminder.dto.ReminderType
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.secrets.lib.EcosSecrets
import ru.citeck.ecos.secrets.lib.secret.EcosSecretType
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import java.time.Instant
import javax.annotation.PostConstruct
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Service
class CertificateExpirationReminder(
    private val recordsService: RecordsService,
    private val bulkMailOperator: BulkMailOperator,
    private val eventsService: EventsService,
    private val bulkMailDao: BulkMailDao,
    private val ecosAppLockService: EcosAppLockService,
    private val dateHelper: CertificateRemindDateHelper
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val SECRET_DAO_ID = "secret"

        private val REMINDER_LOCK_KEY = CertificateExpirationReminder::class.jvmName + "-cert-reminder-sync-lock"
    }

    @PostConstruct
    fun initReminders() {

        eventsService.addListener<RecordChangedEventProps> {
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(RecordChangedEventProps::class.java)
            withTransactional(true)
            withLocal(true)
            withFilter(
                Predicates.and(
                    Predicates.eq("record._type?localId", REMINDER_TYPE_ID),
                    Predicates.eq("record.reminderType.value", ReminderType.CERTIFICATE_EXPIRATION.name),
                    Predicates.eq("record.enabled?bool!", true),
                )
            )
            withAction { event ->
                log.debug { "Reminder created: ${event.record}" }
                AuthContext.runAsSystem {
                    configureReminder(event.record)
                }
            }
        }

        eventsService.addListener<RecordChangedEventProps> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(RecordChangedEventProps::class.java)
            withTransactional(true)
            withLocal(true)
            withFilter(
                Predicates.and(
                    Predicates.eq("record._type?localId", REMINDER_TYPE_ID),
                    Predicates.eq("record.reminderType.value", ReminderType.CERTIFICATE_EXPIRATION.name),
                    Predicates.or(
                        Predicates.eq("diff._has.certificates?bool", true),
                        Predicates.eq("diff._has.notificationTemplate?bool", true),
                        Predicates.eq("diff._has.reminderThresholdDurations?bool", true),
                        Predicates.eq("diff._has.recipients?bool", true),
                        Predicates.eq("diff._has.enabled?bool", true)
                    )
                )
            )
            withAction { event ->
                log.debug { "Reminder changed: ${event.record}" }
                AuthContext.runAsSystem {
                    configureReminder(event.record)
                }
            }
        }

        eventsService.addListener<RecordDeletedEventProps> {
            withEventType(RecordDeletedEvent.TYPE)
            withDataClass(RecordDeletedEventProps::class.java)
            withTransactional(true)
            withLocal(true)
            withFilter(
                Predicates.and(
                    Predicates.eq("record._type?localId", REMINDER_TYPE_ID),
                    Predicates.eq("record.reminderType.value", ReminderType.CERTIFICATE_EXPIRATION.name)
                )
            )
            withAction { event ->
                log.debug { "Reminder deleted: ${event.deferredBulkMails}" }
                AuthContext.runAsSystem {
                    clearReminder(event.deferredBulkMails)
                }
            }
        }

        EcosSecrets.listenChanges { string ->
            AuthContext.runAsSystem {
                ecosAppLockService.doInSyncOrSkip(REMINDER_LOCK_KEY) {
                    configureForSecret(string)
                }
            }
        }
    }

    private fun configureReminder(reminder: Reminder) {
        require(reminder.reminderType == ReminderType.CERTIFICATE_EXPIRATION) {
            "Invalid reminder type: ${reminder.reminderType}"
        }
        reminder.validate()

        val reminderRef = EntityRef.create(REMINDER_SOURCE_ID, reminder.id)

        val currentDeferredBulkMails = recordsService.getAtt(reminderRef, "$REMINDER_ATT_DEFERRED_BULK_MAILS[]?id")
            .asList(EntityRef::class.java)
        val currentBulkMailsDto = bulkMailDao.findAllByExtIds(currentDeferredBulkMails.map { it.getLocalId() })
        val currentBulkMailDtoWrapper = currentBulkMailsDto.map { BulkMailEqualsWrapper(it) }

        val bulkMailFromNewConfig = mutableListOf<BulkMailEqualsWrapper>()
        val bulkMailsToAdd = mutableListOf<BulkMailDto>()
        val bulkMailsToRemove = mutableListOf<EntityRef>()

        if (reminder.enabled) {
            reminder.certificates.forEach { certificate ->

                val certificateData = EcosSecrets.getSecretOrNull(certificate.getLocalId())?.getCertificateDataOrNull()
                    ?: let {
                        log.error { "Certificate not found: ${certificate.getLocalId()}" }
                        return@forEach
                    }

                val expirationDate = certificateData.validityTo
                if (dateHelper.isBeforeNow(expirationDate)) {
                    log.warn { "Certificate already expired: ${certificate.getLocalId()}" }
                    return@forEach
                }

                reminder.reminderThresholdDurations.map { Duration.parse(it) }.forEach { thresholdDuration ->
                    val timeToNotify = dateHelper.getRemindDate(expirationDate, thresholdDuration)
                    if (dateHelper.isBeforeNow(timeToNotify)) {
                        log.warn { "Time to notify already passed: ${certificate.getLocalId()} - $thresholdDuration" }
                        return@forEach
                    }

                    val deferredNotification = BulkMailDto(
                        id = null,
                        name = "Certificate expiration reminder",
                        recipientsData = BulkMailRecipientsDataDto(
                            refs = reminder.recipients
                        ),
                        record = certificate,
                        template = reminder.notificationTemplate,
                        type = NotificationType.EMAIL_NOTIFICATION,
                        config = BulkMailConfigDto(
                            delayedSend = timeToNotify
                        )
                    )

                    bulkMailFromNewConfig.add(deferredNotification.toWrapper())

                    if (deferredNotification.toWrapper() !in currentBulkMailDtoWrapper) {
                        val savedNotification = bulkMailDao.save(deferredNotification)
                        bulkMailOperator.calculateRecipients(savedNotification.extId!!)
                        bulkMailOperator.dispatch(savedNotification.extId)

                        bulkMailsToAdd.add(savedNotification)
                    }
                }
            }
        }

        currentBulkMailsDto.forEach { bulkMail ->
            val wrapper = BulkMailEqualsWrapper(bulkMail)
            if (bulkMail.status == BulkMailStatus.WAIT_FOR_DISPATCH.status && wrapper !in bulkMailFromNewConfig) {
                bulkMailsToRemove.add(bulkMail.recordRef)
                bulkMailDao.remove(bulkMail)
            }
        }

        val updatedDeferredNotifications = currentDeferredBulkMails
            .filter { it !in bulkMailsToRemove }
            .toMutableSet()
        updatedDeferredNotifications.addAll(bulkMailsToAdd.map { it.recordRef })

        val reminderAtts = RecordAtts(reminderRef)
        reminderAtts[REMINDER_ATT_DEFERRED_BULK_MAILS] = updatedDeferredNotifications
        recordsService.mutate(reminderAtts)
    }

    private fun clearReminder(deferredBulkMails: List<EntityRef>) {
        val currentBulkMailsDto = deferredBulkMails.mapNotNull { bulkMailDao.findByExtId(it.getLocalId()) }

        currentBulkMailsDto.forEach { bulkMail ->
            if (bulkMail.status == BulkMailStatus.WAIT_FOR_DISPATCH.status) {
                bulkMailDao.remove(bulkMail)
            }
        }
    }

    private fun configureForSecret(secretId: String) {
        val secret = AuthContext.runAsSystem { EcosSecrets.getSecretOrNull(secretId) } ?: return
        if (secret.getType() != EcosSecretType.CERTIFICATE) {
            return
        }
        val secretRef = EntityRef.create(AppName.EMODEL, SECRET_DAO_ID, secretId)

        val remindersWithSecret = recordsService.query(
            RecordsQuery.create {
                withSourceId(REMINDER_SOURCE_ID)
                withQuery(
                    Predicates.and(
                        Predicates.eq(REMINDER_ATT_REMINDER_TYPE, ReminderType.CERTIFICATE_EXPIRATION.name),
                        Predicates.`in`(REMINDER_ATT_CERTIFICATES, listOf(secretRef))
                    )
                )
                withMaxItems(200)
            },
            Reminder::class.java
        ).getRecords()

        remindersWithSecret.forEach { reminder ->
            configureReminder(reminder)
        }
    }

    private class RecordChangedEventProps(
        val record: Reminder
    )

    private class RecordDeletedEventProps(

        @AttName("record.deferredBulkMails[]?id")
        val deferredBulkMails: List<EntityRef> = emptyList()
    )

    private data class BulkMailEqualsWrapper(
        val bulkMail: BulkMailDto
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false

            other as BulkMailEqualsWrapper

            if (bulkMail.recipientsData != other.bulkMail.recipientsData) return false
            if (bulkMail.record != other.bulkMail.record) return false
            if (bulkMail.template != other.bulkMail.template) return false
            if (bulkMail.type != other.bulkMail.type) return false
            if (bulkMail.config != other.bulkMail.config) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bulkMail.recipientsData.hashCode()
            result = 31 * result + bulkMail.record.hashCode()
            result = 31 * result + bulkMail.template.hashCode()
            result = 31 * result + bulkMail.type.hashCode()
            result = 31 * result + bulkMail.config.hashCode()
            return result
        }
    }

    private fun BulkMailDto.toWrapper() = BulkMailEqualsWrapper(this)
}

@Component
class CertificateRemindDateHelper {

    fun getRemindDate(expirationDate: Instant, thresholdDuration: Duration): Instant {
        return expirationDate.minus(thresholdDuration.toJavaDuration())
    }

    fun isBeforeNow(date: Instant): Boolean {
        return date.isBefore(Instant.now())
    }
}
