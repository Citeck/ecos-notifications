package ru.citeck.ecos.notifications.domain.sender.api.records

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.license.EcosLicense
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.senders.EmailNotificationSenderConfig
import ru.citeck.ecos.notifications.service.senders.NotificationSenderType
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes

@Component
class NotificationsSenderRecordsDao(
    private val notificationsSenderService: NotificationsSenderService
) : AbstractRecordsDao(),
    RecordsDeleteDao,
    RecordAttsDao,
    RecordMutateDtoDao<NotificationsSenderRecord>,
    RecordsQueryDao {

    companion object {
        const val ID = "notifications-sender"
        private val log = KotlinLogging.logger {}
    }

    private val emailSignIsAllowed = EcosLicense.getForEntLib {
        it.get("developer").asBoolean() || it.get("features").has("email-certificate-sign")
    }

    override fun getId(): String {
        return ID
    }

    override fun delete(recordsId: List<String>): List<DelStatus> {
        notificationsSenderService.removeAllByExtId(recordsId)
        return generateSequence { DelStatus.OK }.take(recordsId.size).toList()
    }

    override fun getRecordAtts(recordId: String): NotificationsSenderRecord? {
        if (recordId.isBlank()) {
            return NotificationsSenderRecord()
        }
        val dto = notificationsSenderService.getSenderById(recordId)

        return dto?.let {
            NotificationsSenderRecord(it)
        }
    }

    override fun getRecToMutate(recordId: String): NotificationsSenderRecord {
        return getRecordAtts(recordId) ?: NotificationsSenderRecord()
    }

    override fun saveMutatedRec(record: NotificationsSenderRecord): String {
        record.getEmailSenderConfig()?.validateForFastFail()

        return notificationsSenderService.save(record.toDto()).id!!
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<NotificationsSenderRecord> {
        val result = RecsQueryRes<NotificationsSenderRecord>()
        val sort = recsQuery.sortBy
        val (maxItems, skipCount) = recsQuery.page
        val maxItemsCount = if (maxItems <= 0) 10000 else maxItems

        when (recsQuery.language) {
            PredicateService.LANGUAGE_PREDICATE -> {
                val predicate = recsQuery.getQuery(Predicate::class.java)
                result.setRecords(
                    notificationsSenderService.getAll(maxItemsCount, skipCount, predicate, sort)
                        .map { NotificationsSenderRecord(it) }
                        .toList()
                )
                result.setTotalCount(notificationsSenderService.getCount(predicate))
            }

            else -> {
                log.warn("Unsupported query language '{}'", recsQuery.language)
                val types = if (maxItems < 0) {
                    notificationsSenderService.getAll()
                } else {
                    notificationsSenderService.getAll(maxItems, skipCount, VoidPredicate.INSTANCE, emptyList())
                }
                result.setRecords(types.map { NotificationsSenderRecord(it) }.toList())
                result.setTotalCount(notificationsSenderService.getCount())
            }
        }
        return result
    }

    private fun NotificationsSenderRecord.getEmailSenderConfig(): EmailNotificationSenderConfig? {
        if (NotificationSenderType.fromType(senderType) == NotificationSenderType.DEFAULT &&
            notificationType == NotificationType.EMAIL_NOTIFICATION
        ) {
            return senderConfig.getAs(EmailNotificationSenderConfig::class.java)
        }

        return null
    }

    private fun EmailNotificationSenderConfig.validateForFastFail() {
        if (certSignConfig.enabled) {
            check(emailSignIsAllowed()) {
                "Email certificate signing available only in Enterprise version with email-certificate-sign feature"
            }

            require(certSignConfig.certificate.isNotEmpty()) {
                "Certificate is required for email certificate sign"
            }
        }
    }
}
