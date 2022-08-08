package ru.citeck.ecos.notifications.domain.sender.block

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.BaseMailTest
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.notifications.stringJsonFromResource
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class BlockedNotificationsSendersTest : BaseMailTest() {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private val commandWithTitle = fun(title: String): SendNotificationCommand {
        return SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = RecordRef.EMPTY,
            templateRef = RecordRef.EMPTY,
            title = title,
            body = "its body",
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "ru",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )
    }

    @BeforeEach
    fun setUp() {
        val sender = Json.mapper.convert(
            stringJsonFromResource("sender/block_emails_sender.json"),
            NotificationsSenderDto::class.java
        )!!
        notificationsSenderService.save(sender)
    }

    @AfterEach
    fun clear() {
        notificationRepository.deleteAll()
    }

    @Test
    fun `blocked notification`() {
        val command = commandWithTitle(NotificationSenderSendStatus.BLOCKED.toString())
        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val notifications = notificationRepository.findAll()

        Assertions.assertThat(result!!.result).isEqualTo(NotificationSenderSendStatus.BLOCKED.toString())
        Assertions.assertThat(notifications.size).isEqualTo(1)
        Assertions.assertThat(notifications[0].state).isEqualTo(NotificationState.BLOCKED)

        val emails = greenMail.receivedMessages
        Assertions.assertThat(emails.size).isEqualTo(0)
    }

    @Test
    fun `skip blocked notification should send as default`() {
        val command = commandWithTitle(NotificationSenderSendStatus.SKIPPED.toString())
        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        Assertions.assertThat(result!!.result).isEqualTo(NotificationSenderSendStatus.SENT.toString())

        val notifications = notificationRepository.findAll()

        Assertions.assertThat(notifications.size).isEqualTo(1)
        Assertions.assertThat(notifications[0].state).isEqualTo(NotificationState.SENT)

        val emails = greenMail.receivedMessages
        Assertions.assertThat(emails.size).isEqualTo(1)
    }

    @Test
    fun `failure skipped notification should save failure notification`() {
        greenMail.stop()

        val command = commandWithTitle(NotificationSenderSendStatus.SKIPPED.toString())
        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val allFailures = notificationRepository.findAllByState(NotificationState.ERROR)

        Assertions.assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)
        Assertions.assertThat(allFailures.size).isEqualTo(1)
        Assertions.assertThat(allFailures[0].errorMessage).contains("Mail server connection failed")
    }

    @Test
    fun `failure on block notification should save failure notification`() {
        val command = commandWithTitle("should throw")
        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val allFailures = notificationRepository.findAllByState(NotificationState.ERROR)

        Assertions.assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)
        Assertions.assertThat(allFailures.size).isEqualTo(1)
    }

    @Test
    fun `skipped notification with handle errors should be in sent status`() {
        greenMail.stop()

        val command = commandWithTitle(NotificationSenderSendStatus.SKIPPED.toString())
        commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        Assertions.assertThat(errorFailures.size).isEqualTo(1)

        greenMail.start()
        errorNotificationRepeater.handleErrors()

        val notifications = notificationRepository.findAll()
        Assertions.assertThat(notifications.size).isEqualTo(1)
        Assertions.assertThat(notifications[0].state).isEqualTo(NotificationState.SENT)

        val emails = greenMail.receivedMessages
        Assertions.assertThat(emails.size).isEqualTo(1)
    }

    @Test
    fun `blocked notification with handle errors should be in blocked status`() {
        blockedMailSenderForceState = NotificationSenderSendStatus.SKIPPED.toString()
        greenMail.stop()

        val command = commandWithTitle("never mind")
        commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        Assertions.assertThat(errorFailures.size).isEqualTo(1)

        blockedMailSenderForceState = NotificationSenderSendStatus.BLOCKED.toString()
        greenMail.start()
        errorNotificationRepeater.handleErrors()

        val notifications = notificationRepository.findAll()
        Assertions.assertThat(notifications.size).isEqualTo(1)
        Assertions.assertThat(notifications[0].state).isEqualTo(NotificationState.BLOCKED)

        val emails = greenMail.receivedMessages
        Assertions.assertThat(emails.size).isEqualTo(0)
    }
}
