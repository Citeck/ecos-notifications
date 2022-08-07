package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class HoldFailureNotificationTest: BaseMailTest() {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @AfterEach
    fun clear() {
        notificationRepository.deleteAll()
    }

    @Test
    fun sendNotificationWithPotentialErrorShouldSaveFailureNotification() {
        val command = SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = RecordRef.EMPTY,
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "some_fail",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val allFailures = notificationRepository.findAllByState(NotificationState.ERROR)

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)
        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allFailures[0].errorMessage).contains("Invalid locale format")
    }

    @Test
    fun sendNotificationWithoutMailServerShouldSaveFailureNotification() {
        greenMail.stop()

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

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val allFailures = notificationRepository.findAllByState(NotificationState.ERROR)

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)
        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allFailures[0].errorMessage).contains("Mail server connection failed")
    }
}
