package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.records2.RecordRef
import java.util.*

@RunWith(SpringRunner::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationStoreTest: BaseMailTest() {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

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
}
