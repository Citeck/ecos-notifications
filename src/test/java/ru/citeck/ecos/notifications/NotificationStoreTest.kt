package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import java.util.*

@RunWith(SpringRunner::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationStoreTest: BaseMailTest() {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var recordsService: RecordsService

    @Test
    fun `Success notification store`() {
        val command = SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = RecordRef.create("notifications", "test", "test"),
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        commandsService.executeSync(command, "notifications")

        val allNotifications = notificationRepository.findAll()
        assertThat(allNotifications.size).isEqualTo(1)

        val notification = allNotifications[0]

        assertThat(notification.record).isEqualTo("notifications/test@test")
        assertThat(notification.state).isEqualTo(NotificationState.SENT)
    }

    @Test
    fun `Success notification store with empty ref`() {
        val command = SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = RecordRef.EMPTY,
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        commandsService.executeSync(command, "notifications")

        val allNotifications = notificationRepository.findAll()
        assertThat(allNotifications.size).isEqualTo(1)

        val notification = allNotifications[0]

        assertThat(notification.record).isEqualTo("")
        assertThat(notification.state).isEqualTo(NotificationState.SENT)
    }

    @Test
    fun `Error notification store`() {
        val command = SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = RecordRef.create("notifications", "test", "test"),
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        greenMail.stop()

        commandsService.executeSync(command, "notifications")

        val allNotifications = notificationRepository.findAll()
        assertThat(allNotifications.size).isEqualTo(1)

        val notification = allNotifications[0]

        assertThat(notification.record).isEqualTo("notifications/test@test")
        assertThat(notification.state).isEqualTo(NotificationState.ERROR)
    }

    @Test
    fun `Resend notification`() {
        `Success notification store`()
        val allNotifications = notificationRepository.findAll()
        assertThat(allNotifications.size).isEqualTo(1)
        val notification = allNotifications[0]

        val ex = assertThrows<Exception> {
            recordsService.mutate(
                RecordRef.create("notification", notification.extId),
                mapOf("action" to "RESEND"))
        }

        assertThat(ex.message).contains("Permission denied")


        AuthContext.runAsFull(SimpleAuthData("admin", listOf(AuthRole.ADMIN))) {
            recordsService.mutate(
                RecordRef.create("notification", notification.extId),
                mapOf("action" to "RESEND")
            )
        }

        AuthContext.runAsSystem {
            recordsService.mutate(
                RecordRef.create("notification", notification.extId),
                mapOf("action" to "RESEND")
            )
        }

        val emails = greenMail.receivedMessages
        assertThat(emails.size).isEqualTo(3)
        assertThat(emails[0].subject).isEqualTo(emails[1].subject)
        assertThat(emails[0].content).isEqualTo(emails[1].content)
        assertThat(emails[0].from).isEqualTo(emails[1].from)
        assertThat(emails[0].allRecipients).isEqualTo(emails[1].allRecipients)
        val allAfterResend = notificationRepository.findAll()
        assertThat(allAfterResend.size).isEqualTo(3)
    }
}
