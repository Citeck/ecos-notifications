package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*

@ExtendWith(EcosSpringExtension::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationStoreTest {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var greenMail: GreenMail
    private lateinit var templateModel: MutableMap<String, Any>

    @BeforeEach
    fun setup() {

        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["age"] = "25"

        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)
    }

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

    @AfterEach
    fun stopMailServer() {
        greenMail.stop()
    }
}
