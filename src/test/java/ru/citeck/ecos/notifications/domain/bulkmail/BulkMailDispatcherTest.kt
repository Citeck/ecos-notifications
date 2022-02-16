package ru.citeck.ecos.notifications.domain.bulkmail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.mail.util.MimeMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailBatchConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDispatcher
import ru.citeck.ecos.notifications.domain.notification.service.AwaitingNotificationDispatcher
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.stringJsonFromResource
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.mail.Message
import javax.mail.internet.MimeMessage

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class BulkMailDispatcherTest {

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    @Autowired
    private lateinit var awaitNotificationDispatcher: AwaitingNotificationDispatcher

    @Autowired
    private lateinit var bulkMailDispatcher: BulkMailDispatcher

    @Autowired
    private lateinit var recordsService: RecordsService

    private lateinit var greenMail: GreenMail

    companion object {

        private val harryRef = RecordRef.valueOf("alfresco/user@harry")
        private val severusRef = RecordRef.valueOf("alfresco/user@severus")
        private val nimbusRef = RecordRef.valueOf("broom@nimbus")
        private val templateRef = RecordRef.valueOf(
            "notifications/template@bulk-notification-template"
        )

        private val nimbusRecord = NimbusRecord()
        private val harryRecord = PotterRecord()
        private val severusRecord = SnapeRecord()

        private
        val expectedEnTitle = "Broom: ${nimbusRecord.name}"
        private val expectedEnBody = "Amazing broom Nimbus Two Thousand available now! Date creation: " +
            "${nimbusRecord.dateOfCreation}"
    }

    @Before
    fun setUp() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

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
    fun `bulk mail with template`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)

        assertThat(emails[0].allRecipients.size).isEqualTo(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(harryRecord.email)
        assertEnTitle(emails[0])
        assertEnBody(emails[0])
    }

    @Test
    fun `bulk mail with explicit title body`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                title = "Title",
                body = "Body"
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(2)

        assertThat(emails[0].allRecipients.size).isEqualTo(2)
        assertThat(emails[0].allRecipients.map { it.toString() }.toList())
            .containsExactlyInAnyOrder(severusRecord.email, harryRecord.email)
        assertThat(emails[0].subject).isEqualTo("Title")
        assertThat(GreenMailUtil.getBody(emails[0])).isEqualTo("Body")

        assertThat(emails[1].allRecipients.size).isEqualTo(2)
        assertThat(emails[1].allRecipients.map { it.toString() }.toList())
            .containsExactlyInAnyOrder(severusRecord.email, harryRecord.email)
        assertThat(emails[1].subject).isEqualTo("Title")
        assertThat(GreenMailUtil.getBody(emails[1])).isEqualTo("Body")
    }

    @Test
    fun `bulk mail with explicit title body and without template, record`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                type = NotificationType.EMAIL_NOTIFICATION,
                title = "Title",
                body = "Body"
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(2)

        assertThat(emails[0].allRecipients.size).isEqualTo(2)
        assertThat(emails[0].allRecipients.map { it.toString() }.toList())
            .containsExactlyInAnyOrder(severusRecord.email, harryRecord.email)
        assertThat(emails[0].subject).isEqualTo("Title")
        assertThat(GreenMailUtil.getBody(emails[0])).isEqualTo("Body")

        assertThat(emails[1].allRecipients.size).isEqualTo(2)
        assertThat(emails[1].allRecipients.map { it.toString() }.toList())
            .containsExactlyInAnyOrder(severusRecord.email, harryRecord.email)
        assertThat(emails[1].subject).isEqualTo("Title")
        assertThat(GreenMailUtil.getBody(emails[1])).isEqualTo("Body")
    }

    @Test
    fun `bulk mail ru locale`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    lang = LocaleUtils.toLocale("ru")
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)

        assertThat(emails[0].allRecipients.size).isEqualTo(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(harryRecord.email)
        assertThat(emails[0].subject).isEqualTo("Метла: ${nimbusRecord.name}")
        assertThat(MimeMessageParser(emails[0]).parse().htmlContent.trim()).isEqualTo(
            "Супер быстрая метла Nimbus Two Thousand в продаже! Дата производства: 1993"
        )
    }


    @Test
    fun `bulk mail default config should sent all to recipients`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(2)

        assertThat(emails[0].getRecipients(Message.RecipientType.TO).size).isEqualTo(2)
        assertRecipients(emails[0], Message.RecipientType.TO)
        assertEnTitle(emails[0])
        assertEnBody(emails[0])

        assertThat(emails[1].getRecipients(Message.RecipientType.TO).size).isEqualTo(2)
        assertRecipients(emails[1], Message.RecipientType.TO)
        assertEnTitle(emails[1])
        assertEnBody(emails[1])
    }


    @Test
    fun `bulk mail all cc config`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    allCc = true
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages


        assertThat(emails.size).isEqualTo(2)

        assertThat(emails[0].getRecipients(Message.RecipientType.CC).size).isEqualTo(2)
        assertRecipients(emails[0], Message.RecipientType.CC)
        assertEnTitle(emails[0])
        assertEnBody(emails[0])

        assertThat(emails[1].getRecipients(Message.RecipientType.CC).size).isEqualTo(2)
        assertRecipients(emails[1], Message.RecipientType.CC)
        assertEnTitle(emails[1])
        assertEnBody(emails[1])

    }

    @Test
    fun `bulk mail all bcc config`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    allBcc = true
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages


        assertThat(emails.size).isEqualTo(2)

        //BCC addresses are not contained in the message since other receivers are not allowed to know the list of
        assertThat(emails[0].allRecipients).isNull()
        assertThat(emails[1].allRecipients).isNull()

        assertEnTitle(emails[0])
        assertEnBody(emails[0])

        assertEnTitle(emails[1])
        assertEnBody(emails[1])
    }

    @Test
    fun `bulk mail personalized emails`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    batchConfig = BulkMailBatchConfigDto(
                        personalizedMails = true
                    )
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(2)

        assertThat(emails[0].allRecipients.size).isEqualTo(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(harryRecord.email)
        assertEnTitle(emails[0])
        assertEnBody(emails[0])

        assertThat(emails[1].allRecipients.size).isEqualTo(1)
        assertThat(emails[1].allRecipients[0].toString()).isEqualTo(severusRecord.email)
        assertEnTitle(emails[1])
        assertEnBody(emails[1])
    }

    @Test
    fun `bulk mail recipients from user input`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    fromUserInput = "test1@mail.ru,test2@mail.ru,test3@mail.ru,test4@mail.ru,test5@mail.ru"
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(5)
        assertThat(emails[0].allRecipients.map { it.toString() }.toList())
            .containsExactlyInAnyOrder(
                "test1@mail.ru", "test2@mail.ru", "test3@mail.ru", "test4@mail.ru", "test5@mail.ru"
            )
    }

    @Test
    fun `bulk mail recipients from mixed sources`() {

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                        severusRef
                    ),
                    fromUserInput = "test1@mail.ru,test2@mail.ru,test3@mail.ru,test4@mail.ru,test5@mail.ru"
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(7)
        assertThat(emails[0].allRecipients.map { it.toString() }.toList())
            .containsExactlyInAnyOrder(
                "test1@mail.ru", "test2@mail.ru", "test3@mail.ru", "test4@mail.ru", "test5@mail.ru",
                harryRecord.email, severusRecord.email
            )
    }

    @Test
    fun `bulk mail batching`() {

        var manyEmails = ""

        for (i in 1..200) {
            manyEmails = manyEmails.plus("mail-${i}@mail.ru,")
        }

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    fromUserInput = manyEmails
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    batchConfig = BulkMailBatchConfigDto(
                        size = 7
                    )
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(200)
        assertThat(emails.first().allRecipients.size).isEqualTo(7)
        assertThat(emails.last().allRecipients.size).isEqualTo(4)

    }

    @Test
    fun `bulk mail batching with personalized mails`() {

        var manyEmails = ""

        for (i in 1..200) {
            manyEmails = manyEmails.plus("mail-${i}@mail.ru,")
        }

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    fromUserInput = manyEmails
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    batchConfig = BulkMailBatchConfigDto(
                        size = 7,
                        personalizedMails = true
                    )
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(200)
        assertThat(emails.first().allRecipients.size).isEqualTo(1)
        assertThat(emails.last().allRecipients.size).isEqualTo(1)
    }

    @Test
    fun `bulk mail batching with all cc`() {

        var manyEmails = ""

        for (i in 1..200) {
            manyEmails = manyEmails.plus("mail-${i}@mail.ru,")
        }

        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    fromUserInput = manyEmails
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    batchConfig = BulkMailBatchConfigDto(
                        size = 7,
                    ),
                    allCc = true
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(200)
        assertThat(emails.first().getRecipients(Message.RecipientType.CC).size).isEqualTo(7)
        assertThat(emails.last().getRecipients(Message.RecipientType.CC).size).isEqualTo(4)
    }

    @Test
    fun `bulk mail with expired delayed`() {
        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
                    )
                ),
                record = nimbusRef,
                template = templateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                config = BulkMailConfigDto(
                    delayedSend = Instant.now().minus(5, ChronoUnit.MINUTES)
                )
            )
        )

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)

        assertThat(emails[0].allRecipients.size).isEqualTo(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(harryRecord.email)
        assertEnTitle(emails[0])
        assertEnBody(emails[0])
    }

    @Test
    fun `bulk mail with not expired delayed`() {
        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    recipients = listOf(
                        harryRef,
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

        bulkMailDispatcher.dispatch(bulkMail.extId!!)
        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(0)
    }

    private fun assertEnTitle(msg: MimeMessage) {
        assertThat(msg.subject).isEqualTo(expectedEnTitle)
    }

    private fun assertEnBody(msg: MimeMessage) {
        assertThat(GreenMailUtil.getBody(msg)).isEqualTo(expectedEnBody)
    }

    private fun assertRecipients(msg: MimeMessage, type: Message.RecipientType) {
        assertThat(msg.getRecipients(type).map { it.toString() }.toList())
            .containsExactlyInAnyOrder(severusRecord.email, harryRecord.email)
    }

    @After
    fun stopMailServer() {
        greenMail.stop()
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
