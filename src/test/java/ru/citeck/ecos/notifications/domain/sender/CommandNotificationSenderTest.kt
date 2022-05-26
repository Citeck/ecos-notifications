package ru.citeck.ecos.notifications.domain.sender

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.sender.command.CmdFitNotification
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.providers.EmailNotificationProvider
import ru.citeck.ecos.notifications.stringJsonFromResource
import ru.citeck.ecos.records2.RecordRef
import java.lang.RuntimeException
import java.util.*

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
class CommandNotificationSenderTest {

    @Autowired
    private lateinit var notificationSenderService: NotificationSenderService

    @Autowired
    private lateinit var notificationsSenderService: NotificationsSenderService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var emailProvider: EmailNotificationProvider

    private lateinit var greenMail: GreenMail
    private lateinit var templateModel: MutableMap<String, Any>
    private lateinit var notificationTestTemplate: NotificationTemplateWithMeta
    private lateinit var rawNotification: RawNotification

    companion object {
        private const val COMMAND_TYPE = "send-email-handler"
    }

    @Before
    fun setup() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["age"] = "25"
        templateModel["lastName"] = "Petrenko"
        templateModel["process-definition"] = "flowable\$confirm"

        notificationTestTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/test-template.json"
            ), NotificationTemplateWithMeta::class.java
        )!!
        notificationTemplateService.save(notificationTestTemplate)

        val commandSenderDto = Json.mapper.convert(
            stringJsonFromResource("sender/command_sender_with_condition.json"),
            NotificationsSenderDto::class.java
        )!!

        notificationsSenderService.save(commandSenderDto)

        rawNotification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
            title = "Will be result status",
            template = notificationTestTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )

        commandsService.addExecutor(SendEmailExecutor())
    }

    //обычная отправка
    @Test
    fun sendEmail() {
        val result = notificationSenderService.sendNotification(rawNotification)
        System.out.println("Final result $result")
    }
    //отправка с приложениями
    //блокировка


    inner class SendEmailExecutor : CommandExecutor<SendNotificationCommand> {

        override fun execute(command: SendNotificationCommand): NotificationSenderSendStatus {
            System.out.println(command.notification.title)
            return emailProvider.sendNotification(command.notification.toFit(), null)
            //return NotificationSenderSendStatus.SKIPPED
        }
    }

    @CommandType(COMMAND_TYPE)
    data class SendNotificationCommand(
        val notification: CmdFitNotification
    ) {

    }
}
