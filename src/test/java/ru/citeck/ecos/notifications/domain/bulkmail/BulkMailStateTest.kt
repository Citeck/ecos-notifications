package ru.citeck.ecos.notifications.domain.bulkmail

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.BaseMailTest
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailBatchConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailOperator
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailStatusSynchronizer
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.notification.service.AwaitingNotificationDispatcher
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.stringFromResource
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class BulkMailStateTest : BaseMailTest() {

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    @Autowired
    private lateinit var awaitNotificationDispatcher: AwaitingNotificationDispatcher

    @Autowired
    private lateinit var bulkMailOperator: BulkMailOperator

    @Autowired
    private lateinit var bulkMailStatusSynchronizer: BulkMailStatusSynchronizer

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    @Autowired
    private lateinit var notificationDao: NotificationDao

    @Autowired
    private lateinit var recordsService: RecordsService

    companion object {

        private val harryRef = EntityRef.valueOf("emodel/person@harry")
        private val severusRef = EntityRef.valueOf("emodel/person@severus")
        private val nimbusRef = EntityRef.valueOf("broom@nimbus")
        private val templateRef = EntityRef.valueOf(
            "notifications/template@bulk-notification-template"
        )

        private val nimbusRecord = NimbusRecord()
    }

    @BeforeEach
    fun setUp() {

        val notificationTemplate = Json.mapper.convert(
            stringFromResource("template/bulk/bulk-notification-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create("emodel/person")
                .addRecord(
                    harryRef.getLocalId(),
                    PotterRecord()
                )
                .addRecord(
                    severusRef.getLocalId(),
                    SnapeRecord()
                )
                .build()
        )

        recordsService.register(
            RecordsDaoBuilder.create(nimbusRef.getSourceId())
                .addRecord(
                    nimbusRef.getLocalId(),
                    nimbusRecord
                )
                .build()
        )
    }

    @Test
    fun `bulk mail should be saved in new status`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        assertThat(bulkMail.status).isEqualTo(BulkMailStatus.NEW.status)
    }

    @Test
    fun `success bulk mail dispatch should be in sent status`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        bulkMailOperator.calculateRecipients(bulkMail.extId!!)
        bulkMailOperator.dispatch(bulkMail.extId!!)

        awaitNotificationDispatcher.dispatchNotifications()
        bulkMailStatusSynchronizer.sync()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(2)

        val updatedBulkMail = bulkMailDao.findByExtId(bulkMail.extId!!)
        assertThat(updatedBulkMail!!.status).isEqualTo(BulkMailStatus.SENT.status)
    }

    @Test
    fun `bulk mail with delayed notifications should be in wait for dispatch status`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    delayedSend = Instant.now().plus(5, ChronoUnit.MINUTES)
                )
            )
        )

        bulkMailOperator.calculateRecipients(bulkMail.extId!!)
        bulkMailOperator.dispatch(bulkMail.extId!!)

        awaitNotificationDispatcher.dispatchNotifications()
        bulkMailStatusSynchronizer.sync()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(0)

        val updatedBulkMail = bulkMailDao.findByExtId(bulkMail.extId!!)
        assertThat(updatedBulkMail!!.status).isEqualTo(BulkMailStatus.WAIT_FOR_DISPATCH.status)
    }

    @Test
    fun `bulk mail with error notifications should be in trying to dispatch status`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION
            )
        )

        greenMail.stop()

        bulkMailOperator.calculateRecipients(bulkMail.extId!!)
        bulkMailOperator.dispatch(bulkMail.extId!!)

        awaitNotificationDispatcher.dispatchNotifications()
        bulkMailStatusSynchronizer.sync()

        val updatedBulkMail = bulkMailDao.findByExtId(bulkMail.extId!!)
        assertThat(updatedBulkMail!!.status).isEqualTo(BulkMailStatus.TRYING_TO_DISPATCH.status)
    }

    @Test
    fun `bulk mail with expired notifications should be in error status`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION
            )
        )

        greenMail.stop()

        bulkMailOperator.calculateRecipients(bulkMail.extId!!)
        bulkMailOperator.dispatch(bulkMail.extId!!)

        awaitNotificationDispatcher.dispatchNotifications()

        // retries are driven explicitly: each tick fails again until the attempts
        // budget (test retry.max-attempts) is exhausted and rows become EXPIRED
        Awaitility.await().atMost(Duration.ofSeconds(40)).untilAsserted {

            errorNotificationRepeater.handleErrors()
            bulkMailStatusSynchronizer.sync()
            val updatedBulkMail = bulkMailDao.findByExtId(bulkMail.extId!!)
            assertThat(updatedBulkMail!!.status).isEqualTo(BulkMailStatus.ERROR.status)
        }
    }

    @Test
    fun `bulk mail trying to dispatch to sent transition`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION
            )
        )

        greenMail.stop()

        bulkMailOperator.calculateRecipients(bulkMail.extId!!)
        bulkMailOperator.dispatch(bulkMail.extId!!)

        awaitNotificationDispatcher.dispatchNotifications()

        bulkMailStatusSynchronizer.sync()

        assertThat(bulkMailDao.findByExtId(bulkMail.extId!!)!!.status).isEqualTo(
            BulkMailStatus.TRYING_TO_DISPATCH.status
        )

        greenMail.start()

        Awaitility.await().atMost(Duration.ofSeconds(40)).untilAsserted {

            errorNotificationRepeater.handleErrors()
            bulkMailStatusSynchronizer.sync()
            assertThat(bulkMailDao.findByExtId(bulkMail.extId!!)!!.status).isEqualTo(BulkMailStatus.SENT.status)
        }
    }

    @Test
    fun `bulk mail removal should cancel error notifications and stop retries`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    // one notification row per recipient — the cancellation must cover them all
                    batchConfig = BulkMailBatchConfigDto(personalizedMails = true)
                )
            )
        )

        greenMail.stop()

        bulkMailOperator.calculateRecipients(bulkMail.extId!!)
        bulkMailOperator.dispatch(bulkMail.extId!!)

        awaitNotificationDispatcher.dispatchNotifications()

        val errorNotifications = notificationDao.findNotificationForBulkMail(
            bulkMail.recordRef.toString(),
            NotificationState.ERROR
        )
        assertThat(errorNotifications).hasSize(2)

        bulkMailDao.remove(bulkMailDao.findByExtId(bulkMail.extId!!)!!)

        val cancelled = notificationDao.findNotificationForBulkMail(bulkMail.recordRef.toString())
        assertThat(cancelled).hasSize(2)
        assertThat(cancelled).allSatisfy {
            assertThat(it.state).isEqualTo(NotificationState.CANCELLED)
            assertThat(it.nextRetryAt).isNull()
        }

        // SMTP is back, but cancelled rows must not be picked up by the retry tick
        greenMail.start()
        errorNotificationRepeater.handleErrors()

        assertThat(greenMail.receivedMessages).isEmpty()
        assertThat(notificationDao.findNotificationForBulkMail(bulkMail.recordRef.toString()))
            .allSatisfy { assertThat(it.state).isEqualTo(NotificationState.CANCELLED) }
    }

    class NimbusRecord(

        @AttName("name")
        val name: String = "Nimbus Two Thousand",

        @AttName("dateOfCreation")
        val dateOfCreation: Int = 1993

    )

    class PotterRecord(

        @AttName("email")
        val email: String = "harry.potter@hogwarts.com"

    )

    class SnapeRecord(

        @AttName("email")
        val email: String = "severus.snape@hogwarts.com"
    )
}
