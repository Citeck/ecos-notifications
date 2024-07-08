package ru.citeck.ecos.notifications.domain.sender

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.util.MimeTypeUtils
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.*
import ru.citeck.ecos.notifications.domain.notification.*
import ru.citeck.ecos.notifications.domain.sender.command.AttachmentData
import ru.citeck.ecos.notifications.domain.sender.command.CmdFitNotification
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.providers.EmailNotificationProvider
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*
import javax.mail.internet.MimeMultipart

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class CommandNotificationSenderTest : BaseMailTest() {

    @Autowired
    private lateinit var notificationSenderService: NotificationSenderService

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var emailProvider: EmailNotificationProvider

    private lateinit var rawNotification: RawNotification

    companion object {
        private const val COMMAND_TYPE = "send-email-handler"
        private const val TEST_SUBJECT = "Test command sender"
        private const val RECIPIENT_EMAIL = TestUtils.RECIPIENT_EMAIL
        private const val CONDITIONAL_EMAIL_SENDER = "command-email-sender-with-condition"

        private const val ATTACHMENT_CONTENT =
            "XHUwNDIyXHUwNDM1XHUwNDQxXHUwNDQyXHUwNDNlXHUwNDMyXHUwNDNlXHUwNDM1XHUwMDIwXHUw" +
                "NDQxXHUwNDNlXHUwNDM0XHUwNDM1XHUwNDQwXHUwNDM2XHUwNDM4XHUwNDNjXHUwNDNlXHUwNDM1" +
                "XHUwMDIwXHUwNDNmXHUwNDQwXHUwNDM4XHUwNDNiXHUwNDNlXHUwNDM2XHUwNDM1XHUwNDNkXHUw" +
                "NDM4XHUwNDRmXHUwMDNhXHUwMDBkXHUwMDBhXHUwNDFmXHUwNDQwXHUwNDM1XHUwNDM0XHUwNDNi" +
                "XHUwNDMwXHUwNDMzXHUwNDMwXHUwNDM1XHUwNDNjXHUwMDIwXHUwNDQwXHUwNDMwXHUwNDMxXHUw" +
                "NDNlXHUwNDQyXHUwNDMwXHUwNDQyXHUwNDRjXHUwMDIwXHUwMDBkXHUwMDBh"
        private const val DECODED_ATTACHMENT_CONTENT = "\\u0422\\u0435\\u0441\\u0442\\u043e\\u0432\\u043e\\u0435" +
            "\\u0020\\u0441\\u043e\\u0434\\u0435\\u0440\\u0436\\u0438\\u043c\\u043e\\u0435\\u0020\\u043f\\u0440" +
            "\\u0438\\u043b\\u043e\\u0436\\u0435\\u043d\\u0438\\u044f\\u003a\\u000d\\u000a\\u041f\\u0440\\u0435" +
            "\\u0434\\u043b\\u0430\\u0433\\u0430\\u0435\\u043c\\u0020\\u0440\\u0430\\u0431\\u043e\\u0442\\u0430" +
            "\\u0442\\u044c\\u0020\\u000d\\u000a"
    }

    @BeforeEach
    fun setup() {
        templateModel["process-definition"] = "flowable\$confirm"

        val commandSenderDto = Json.mapper.convert(
            stringJsonFromResource("sender/command_sender_with_condition.json"),
            NotificationsSenderDto::class.java
        )!!

        notificationsSenderService.save(commandSenderDto)

        rawNotification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            title = "Will be result status",
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )

        commandsService.addExecutor(SendEmailExecutor())
    }

    @AfterEach
    fun tearDown() {
        notificationsSenderService.delete(CONDITIONAL_EMAIL_SENDER)
    }

    @Test
    fun `send email through command sender`() {
        notificationSenderService.sendNotification(rawNotification)
        val emails = greenMail.receivedMessages

        assertEquals(1, emails.size)
        assertEquals(TEST_SUBJECT, emails[0].subject)
        assertEquals(RECIPIENT_EMAIL, emails[0].allRecipients[0].toString())
    }

    @Test
    fun `send email with attachment based on preview info model through command sender`() {
        val model = templateModel

        model[NOTIFICATION_ATTACHMENTS] = mapOf(
            NOTIFICATION_ATTACHMENT_BYTES to ATTACHMENT_CONTENT,
            NOTIFICATION_ATTACHMENTS_PREVIEW_INFO to mapOf(
                NOTIFICATION_ATTACHMENT_ORIGINAL_NAME to TestUtils.TEXT_TXT_FILENAME,
                NOTIFICATION_ATTACHMENT_ORIGINAL_EXT to TestUtils.TEXT_TXT_EXT,
                NOTIFICATION_ATTACHMENT_MIMETYPE to MimeTypeUtils.TEXT_PLAIN.toString()
            )
        )
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(EmailNotificationTest.RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = model,
            from = "test@mail.ru"
        )
        notificationSenderService.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertEquals(1, emails.size)
        assertEquals(TEST_SUBJECT, emails[0].subject)
        assertEquals(RECIPIENT_EMAIL, emails[0].allRecipients[0].toString())

        val content = emails[0].content

        assertTrue(content is MimeMultipart)
        assertEquals(2, (content as MimeMultipart).count)

        val isHaveAttachment = hasAttachment(
            content,
            MimeTypeUtils.TEXT_PLAIN.toString(),
            TestUtils.TEXT_TXT_FILENAME,
            "us-ascii",
            DECODED_ATTACHMENT_CONTENT
        )
        assertTrue(isHaveAttachment)
    }

    @Test
    fun `send email with attachment based on meta model through command sender`() {
        val model = templateModel

        model[NOTIFICATION_ATTACHMENTS] = mapOf(
            NOTIFICATION_ATTACHMENT_BYTES to ATTACHMENT_CONTENT,
            NOTIFICATION_ATTACHMENT_META to mapOf(
                NOTIFICATION_ATTACHMENT_NAME to TestUtils.TEXT_TXT_FILENAME,
                NOTIFICATION_ATTACHMENT_EXT to TestUtils.TEXT_TXT_EXT,
                NOTIFICATION_ATTACHMENT_MIMETYPE to MimeTypeUtils.TEXT_PLAIN.toString()
            )
        )
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(EmailNotificationTest.RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = model,
            from = "test@mail.ru"
        )
        notificationSenderService.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertEquals(1, emails.size)
        assertEquals(TEST_SUBJECT, emails[0].subject)
        assertEquals(RECIPIENT_EMAIL, emails[0].allRecipients[0].toString())

        val content = emails[0].content

        assertTrue(content is MimeMultipart)
        assertEquals(2, (content as MimeMultipart).count)

        val isHaveAttachment = hasAttachment(
            content,
            MimeTypeUtils.TEXT_PLAIN.toString(),
            TestUtils.TEXT_TXT_FILENAME,
            "us-ascii",
            DECODED_ATTACHMENT_CONTENT
        )
        assertTrue(isHaveAttachment)
    }

    @Test
    fun `block email by command sender`() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            title = NotificationSenderSendStatus.BLOCKED.toString(),
            body = "Simple content for test purpose",
            model = templateModel,
            from = "test@mail.ru"
        )
        assertEquals(
            NotificationSenderSendStatus.BLOCKED,
            notificationSenderService.sendNotification(notification)
        )
    }

    /**
     * Implements command executor
     */
    inner class SendEmailExecutor : CommandExecutor<SendNotificationCommand> {

        override fun execute(command: SendNotificationCommand): NotificationSenderSendStatus {
            return if (command.title == NotificationSenderSendStatus.BLOCKED.toString()) {
                return NotificationSenderSendStatus.BLOCKED
            } else {
                emailProvider.sendNotification(
                    FitNotification(
                        command.body,
                        TEST_SUBJECT,
                        command.recipients,
                        command.from,
                        command.cc,
                        command.bcc,
                        command.webUrl,
                        CmdFitNotification.convertAttachments(command.attachments),
                        command.data,
                        command.templateRef
                    ),
                    Unit
                )
            }
        }
    }

    /**
     * Defines command class for type [COMMAND_TYPE].
     * Command class must contain the same list of properties as [CmdFitNotification]
     */
    @CommandType(COMMAND_TYPE)
    data class SendNotificationCommand(
        val body: String,
        var title: String? = "",
        val recipients: Set<String>,
        val from: String,
        var cc: Set<String> = emptySet(),
        var bcc: Set<String> = emptySet(),
        var webUrl: String = "",
        var attachments: Map<String, AttachmentData> = emptyMap(),
        var data: Map<String, Any> = emptyMap(),
        var templateRef: RecordRef?
    )
}
