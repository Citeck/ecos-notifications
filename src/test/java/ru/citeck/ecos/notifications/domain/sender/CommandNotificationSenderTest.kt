package ru.citeck.ecos.notifications.domain.sender

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.MimeTypeUtils
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.*
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.NotificationConstants
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.sender.command.AttachmentData
import ru.citeck.ecos.notifications.domain.sender.command.CmdFitNotification
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.service.providers.EmailNotificationProvider
import ru.citeck.ecos.records2.RecordRef
import java.util.*
import javax.mail.internet.MimeMultipart

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
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

    @Before
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

    @Test
    fun sendEmailThroughCommandSender() {
        notificationSenderService.sendNotification(rawNotification)
        val emails = greenMail.receivedMessages

        Assert.assertEquals(1, emails.size)
        Assert.assertEquals(TEST_SUBJECT, emails[0].subject)
        Assert.assertEquals(RECIPIENT_EMAIL, emails[0].allRecipients[0].toString())
    }

    @Test
    fun sendEmailWithAttachmentThroughCommandSender() {
        val model = templateModel

        model[NotificationConstants.ATTACHMENTS] = mapOf(
            NotificationConstants.BYTES to ATTACHMENT_CONTENT,
            NotificationConstants.PREVIEW_INFO to mapOf(
                NotificationConstants.ORIGINAL_NAME to TestUtils.TEXT_TXT_FILENAME,
                NotificationConstants.ORIGINAL_EXT to TestUtils.TEXT_TXT_EXT,
                NotificationConstants.MIMETYPE to MimeTypeUtils.TEXT_PLAIN.toString()
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

        Assert.assertEquals(1, emails.size)
        Assert.assertEquals(TEST_SUBJECT, emails[0].subject)
        Assert.assertEquals(RECIPIENT_EMAIL, emails[0].allRecipients[0].toString())

        val content = emails[0].content

        Assert.assertTrue(content is MimeMultipart)
        Assert.assertEquals(2, (content as MimeMultipart).count)

        var isHaveAttachment = hasAttachment(
            content, MimeTypeUtils.TEXT_PLAIN.toString(),
            TestUtils.TEXT_TXT_FILENAME, "us-ascii", DECODED_ATTACHMENT_CONTENT
        )
        Assert.assertTrue(isHaveAttachment)
    }

    @Test
    fun blockEmailByCommandSender() {
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
        Assert.assertEquals(NotificationSenderSendStatus.BLOCKED,
            notificationSenderService.sendNotification(notification))
    }

    /**
     * Implements command executor
     */
    inner class SendEmailExecutor : CommandExecutor<SendNotificationCommand> {

        override fun execute(command: SendNotificationCommand): NotificationSenderSendStatus {
            var resultStatus: NotificationSenderSendStatus? = null
            if (command.title != null) {
                try {
                    resultStatus = NotificationSenderSendStatus.valueOf(command.title!!)
                } catch (e: java.lang.IllegalArgumentException) {
                }
            }
            return resultStatus ?: emailProvider.sendNotification(
                FitNotification(
                    command.body,
                    TEST_SUBJECT,
                    command.recipients,
                    command.from,
                    command.cc,
                    command.bcc,
                    CmdFitNotification.convertAttachments(command.attachments),
                    command.data
                ), null
            )
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
        var attachments: Map<String, AttachmentData> = emptyMap(),
        var data: Map<String, Any> = emptyMap()
    )
}
