package ru.citeck.ecos.notifications.domain.reminder

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.domain.reminder.config.*
import ru.citeck.ecos.notifications.domain.reminder.dto.Reminder
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.stringFromResource
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.secrets.lib.provider.InMemSecretsProvider
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class CertificateExpirationReminderTest {

    @Autowired
    private lateinit var notificationDao: NotificationDao

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    private lateinit var reminderRef: EntityRef

    companion object {

        private val userIvanRef = "emodel/person@ivan.petrov".toEntityRef()
        private val userKatya = "emodel/person@katya.dubkina".toEntityRef()
        private val userPetya = "emodel/person@petya.pupkin".toEntityRef()

        private const val CERT_RECORD = "emodel/secret@cert-reminder-test"
        private const val CERT_RECORD_2 = "emodel/secret@cert-reminder-test-2"

        private val templateRef = "notifications/template@default-certificate-expiration-template".toEntityRef()
    }

    @BeforeEach
    fun setUp() {
        localAppService.deployLocalArtifacts()

        recordsService.register(
            RecordsDaoBuilder.create("emodel/person")
                .addRecord(
                    userIvanRef.getLocalId(),
                    IvanRecord()
                )
                .addRecord(
                    userKatya.getLocalId(),
                    KatyaRecord()
                )
                .addRecord(
                    userPetya.getLocalId(),
                    PetyaRecord()
                )
                .build()
        )

        val privateKey = ResourceUtils.getFile("classpath:mail-test-certs/private_key_junit.pem").readText()
        val certString = ResourceUtils.getFile("classpath:mail-test-certs/certificate_junit.pem").readText()

        InMemSecretsProvider.registerCertificate("cert-reminder-test", privateKey, certString)
        InMemSecretsProvider.registerCertificate("cert-reminder-test-2", privateKey, certString)

        val privateKeyExpired = ResourceUtils.getFile("classpath:mail-test-certs/private_key_junit_expired.pem")
            .readText()
        val certExpiredString = ResourceUtils.getFile("classpath:mail-test-certs/certificate_junit_expired.pem")
            .readText()
        InMemSecretsProvider.registerCertificate("email-sign-expired-cert", privateKeyExpired, certExpiredString)

        val reminderData = Json.mapper.convert(
            stringFromResource("reminder/test-cert-expiration-reminder.yml"),
            Reminder::class.java
        )!!
        reminderRef = recordsService.mutate(EntityRef.create(REMINDER_SOURCE_ID, ""), reminderData)
    }

    @AfterEach
    fun tearDown() {
        recordsService.delete(reminderRef)
        bulkMailDao.getAll().forEach { bulkMailDao.remove(it) }
        notificationRepository.deleteAll()
    }

    @ParameterizedTest
    @ValueSource(strings = [CERT_RECORD, CERT_RECORD_2])
    fun `create reminder should schedule notifications`(certificateRecord: String) {

        val scheduledNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
        assertThat(scheduledNotifications).hasSize(4)
        assertThat(scheduledNotifications).allMatch { it.template == templateRef }
        assertThat(scheduledNotifications).allMatch { it.record == certificateRecord.toEntityRef() }
        assertThat(scheduledNotifications).allMatch { it.type == NotificationType.EMAIL_NOTIFICATION }
        assertThat(scheduledNotifications).allMatch { it.state == NotificationState.WAIT_FOR_DISPATCH }

        // Cert valid to 3005-01-16T08:08:46Z

        // 15d before
        assertThat(scheduledNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-01T08:08:46Z") }

        // 5d before
        assertThat(scheduledNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-11T08:08:46Z") }

        // 1d before
        assertThat(scheduledNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-15T08:08:46Z") }

        // 1h 30m before
        assertThat(scheduledNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-16T06:38:46Z") }
    }

    @ParameterizedTest
    @ValueSource(strings = [CERT_RECORD, CERT_RECORD_2])
    fun `disable reminder should cancel scheduled notifications`(certificateRecord: String) {

        var scheduledNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
        assertThat(scheduledNotifications).hasSize(4)
        assertThat(scheduledNotifications).allMatch { it.state == NotificationState.WAIT_FOR_DISPATCH }

        val atts = RecordAtts(reminderRef)
        atts[REMINDER_ATT_ENABLED] = false
        recordsService.mutate(atts)

        scheduledNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())

        assertThat(scheduledNotifications).hasSize(4)
        assertThat(scheduledNotifications).allMatch { it.state == NotificationState.CANCELLED }
    }

    @Test
    fun `disable reminder should delete bulk mail`() {

        var scheduledBulkMails = getBulkMailsForReminderByAssoc(reminderRef)
        assertThat(scheduledBulkMails).hasSize(8)

        val atts = RecordAtts(reminderRef)
        atts[REMINDER_ATT_ENABLED] = false
        recordsService.mutate(atts)

        scheduledBulkMails.map { it.extId }.forEach {
            val bulkMail = bulkMailDao.findByExtId(it!!)
            assertThat(bulkMail).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [CERT_RECORD, CERT_RECORD_2])
    fun `change reminder threshold should recalculate referred notifications`(certificateRecord: String) {

        val atts = RecordAtts(reminderRef)
        atts[REMINDER_ATT_THRESHOLD_DURATIONS] = listOf("15d", "3d", "1d", "1h")
        recordsService.mutate(atts)

        val allNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
        assertThat(allNotifications).hasSize(6)

        val cancelledNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
            .filter { it.state == NotificationState.CANCELLED }
        assertThat(cancelledNotifications).hasSize(2)

        val newNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
            .filter { it.state == NotificationState.WAIT_FOR_DISPATCH }
        assertThat(newNotifications).hasSize(4)

        // Cert valid to 3005-01-16T08:08:46Z

        // 15d before
        assertThat(newNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-01T08:08:46Z") }

        // 3d before
        assertThat(newNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-13T08:08:46Z") }

        // 1d before
        assertThat(newNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-15T08:08:46Z") }

        // 1h before
        assertThat(newNotifications).anyMatch { it.delayedSend == Instant.parse("3005-01-16T07:08:46Z") }
    }

    @ParameterizedTest
    @ValueSource(strings = [CERT_RECORD, CERT_RECORD_2])
    fun `change reminder threshold check recalculate bulk mails`(certificateRecord: String) {

        val sourceBulkMails = queryBulkMailsForRecord(certificateRecord.toEntityRef())
        assertThat(sourceBulkMails).hasSize(4)

        val atts = RecordAtts(reminderRef)
        atts[REMINDER_ATT_THRESHOLD_DURATIONS] = listOf("15d", "3d", "1d", "1h")
        recordsService.mutate(atts)

        val bulkMailsAfterUpdate = queryBulkMailsForRecord(certificateRecord.toEntityRef())
        assertThat(bulkMailsAfterUpdate).hasSize(4)
        assertThat(bulkMailsAfterUpdate).allMatch { it.status == BulkMailStatus.WAIT_FOR_DISPATCH.status }
    }

    @ParameterizedTest
    @ValueSource(strings = [CERT_RECORD, CERT_RECORD_2])
    fun `change recipients should recalculate referred notifications`(certificateRecord: String) {

        val atts = RecordAtts(reminderRef)
        atts[REMINDER_ATT_RECIPIENTS] = listOf(userPetya)
        recordsService.mutate(atts)

        var cancelledNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
            .filter { it.state == NotificationState.CANCELLED }
        assertThat(cancelledNotifications).hasSize(4)

        var newNotifications = getNotificationsForRecord(certificateRecord.toEntityRef())
            .filter { it.state == NotificationState.WAIT_FOR_DISPATCH }
        assertThat(newNotifications).hasSize(4)
    }

    @Test
    fun `change certificate list should recalculate notifications`() {

        val initialNotifications = getNotificationsForReminder(reminderRef)
        assertThat(initialNotifications).hasSize(8)
        assertThat(initialNotifications).allMatch { it.state == NotificationState.WAIT_FOR_DISPATCH }

        val atts = RecordAtts(reminderRef)
        atts[REMINDER_ATT_CERTIFICATES] = listOf(CERT_RECORD_2)
        recordsService.mutate(atts)

        val cancelledNotifications = getNotificationsForReminder(reminderRef)

        assertThat(cancelledNotifications).hasSize(4)
        assertThat(cancelledNotifications).allMatch { it.state == NotificationState.WAIT_FOR_DISPATCH }

        val notificationForCert = getNotificationsForRecord(CERT_RECORD.toEntityRef())
        assertThat(notificationForCert).hasSize(4)
        assertThat(notificationForCert).allMatch { it.state == NotificationState.CANCELLED }

        val notificationForCert2 = getNotificationsForRecord(CERT_RECORD_2.toEntityRef())
        assertThat(notificationForCert2).hasSize(4)

        assertThat(notificationForCert2.filter { it.state == NotificationState.WAIT_FOR_DISPATCH }).hasSize(4)
    }

    @Test
    fun `remove reminder should cancel all notifications`() {

        val scheduledNotifications = getNotificationsForReminder(reminderRef)
        assertThat(scheduledNotifications).hasSize(8)
        assertThat(scheduledNotifications).allMatch { it.state == NotificationState.WAIT_FOR_DISPATCH }

        recordsService.delete(reminderRef)

        val cancelledNotificationsRecord1 = getNotificationsForRecord(CERT_RECORD.toEntityRef())
        assertThat(cancelledNotificationsRecord1).hasSize(4)
        assertThat(cancelledNotificationsRecord1).allMatch { it.state == NotificationState.CANCELLED }

        val cancelledNotificationsRecord2 = getNotificationsForRecord(CERT_RECORD_2.toEntityRef())
        assertThat(cancelledNotificationsRecord2).hasSize(4)
        assertThat(cancelledNotificationsRecord2).allMatch { it.state == NotificationState.CANCELLED }
    }

    @Test
    fun `remove reminder should delete all bulk mails`() {

        val scheduledBulkMails = getBulkMailsForReminderByAssoc(reminderRef)
        assertThat(scheduledBulkMails).hasSize(8)

        recordsService.delete(reminderRef)

        scheduledBulkMails.map { it.extId }.forEach {
            val bulkMail = bulkMailDao.findByExtId(it!!)
            assertThat(bulkMail).isNull()
        }
    }

    @Test
    fun `change secret certificate data should recalculate notifications`() {

        // update existing secret with new certificate
        // Cert valid to 2524-07-22T10:53:49Z
        val privateKey2 = ResourceUtils.getFile("classpath:mail-test-certs/private_key_junit_2.pem").readText()
        val certString2 = ResourceUtils.getFile("classpath:mail-test-certs/certificate_junit_2.pem").readText()

        InMemSecretsProvider.registerCertificate("cert-reminder-test", privateKey2, certString2)

        val canceledNotifications = getNotificationsForRecord(CERT_RECORD.toEntityRef())
            .filter { it.state == NotificationState.CANCELLED }
        assertThat(canceledNotifications).hasSize(4)

        val newScheduledNotifications = getNotificationsForRecord(CERT_RECORD.toEntityRef())
            .filter { it.state == NotificationState.WAIT_FOR_DISPATCH }
        assertThat(newScheduledNotifications).hasSize(4)

        // 15d before
        assertThat(newScheduledNotifications).anyMatch { it.delayedSend == Instant.parse("2524-07-07T10:53:49Z") }

        // 5d before
        assertThat(newScheduledNotifications).anyMatch { it.delayedSend == Instant.parse("2524-07-17T10:53:49Z") }

        // 1d before
        assertThat(newScheduledNotifications).anyMatch { it.delayedSend == Instant.parse("2524-07-21T10:53:49Z") }

        // 1h 30m before
        assertThat(newScheduledNotifications).anyMatch { it.delayedSend == Instant.parse("2524-07-22T09:23:49Z") }

        val notificationsForCert2 = getNotificationsForRecord(CERT_RECORD_2.toEntityRef())
            .filter { it.state == NotificationState.WAIT_FOR_DISPATCH }

        assertThat(notificationsForCert2).hasSize(4)

        // 15d before
        assertThat(notificationsForCert2).anyMatch { it.delayedSend == Instant.parse("3005-01-01T08:08:46Z") }

        // 5d before
        assertThat(notificationsForCert2).anyMatch { it.delayedSend == Instant.parse("3005-01-11T08:08:46Z") }

        // 1d before
        assertThat(notificationsForCert2).anyMatch { it.delayedSend == Instant.parse("3005-01-15T08:08:46Z") }

        // 1h 30m before
        assertThat(notificationsForCert2).anyMatch { it.delayedSend == Instant.parse("3005-01-16T06:38:46Z") }
    }

    private fun getNotificationsForRecord(recordRef: EntityRef): List<NotificationDto> {
        return notificationDao.findByRecord(recordRef.toString())
    }

    private fun getNotificationsForReminder(reminderRef: EntityRef): List<NotificationDto> {
        return getBulkMailsForReminderByAssoc(reminderRef)
            .map { dto -> notificationDao.findNotificationForBulkMail(dto.recordRef.toString()) }
            .flatten()
    }

    private fun getBulkMailsForReminderByAssoc(reminderRef: EntityRef): List<BulkMailDto> {
        val currentDeferredBulkMails = recordsService.getAtt(reminderRef, "$REMINDER_ATT_DEFERRED_BULK_MAILS[]?id")
            .asList(EntityRef::class.java)
        return currentDeferredBulkMails.mapNotNull { bulkMailDao.findByExtId(it.getLocalId()) }
    }

    private fun queryBulkMailsForRecord(reminderRef: EntityRef): List<BulkMailDto> {
        return bulkMailDao.getAll().filter { it.record == reminderRef }
    }

    private class IvanRecord(

        @AttName("email")
        val email: String = "ivan@mail.com"

    )

    private class KatyaRecord(

        @AttName("email")
        val email: String = "katya@mail.com"

    )

    private class PetyaRecord(

        @AttName("email")
        val email: String = "petya@mail.com"
    )
}
