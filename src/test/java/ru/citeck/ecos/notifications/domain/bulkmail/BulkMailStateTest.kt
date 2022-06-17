package ru.citeck.ecos.notifications.domain.bulkmail

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.BaseMailTest
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailOperator
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailStatusSynchronizer
import ru.citeck.ecos.notifications.domain.notification.service.AwaitingNotificationDispatcher
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.stringJsonFromResource
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner::class)
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
    private lateinit var recordsService: RecordsService

    companion object {

        private val harryRef = RecordRef.valueOf("alfresco/user@harry")
        private val severusRef = RecordRef.valueOf("alfresco/user@severus")
        private val nimbusRef = RecordRef.valueOf("broom@nimbus")
        private val templateRef = RecordRef.valueOf(
            "notifications/template@bulk-notification-template"
        )

        private val nimbusRecord = NimbusRecord()
    }

    @Before
    fun setUp() {

        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/bulk/bulk-notification-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create("alfresco/user")
                .addRecord(
                    harryRef.id,
                    PotterRecord()
                )
                .addRecord(
                    severusRef.id,
                    SnapeRecord()
                )
                .build()
        )

        recordsService.register(
            RecordsDaoBuilder.create(nimbusRef.sourceId)
                .addRecord(
                    nimbusRef.id,
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

        Awaitility.await().atMost(Duration.ofSeconds(40)).untilAsserted {

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

            bulkMailStatusSynchronizer.sync()
            assertThat(bulkMailDao.findByExtId(bulkMail.extId!!)!!.status).isEqualTo(BulkMailStatus.SENT.status)

        }

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
