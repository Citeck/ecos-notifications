package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationRepository
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef


@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class HoldFailureNotificationTest {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var failureNotificationRepository: FailureNotificationRepository

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    private lateinit var greenMail: GreenMail
    private lateinit var templateModel: MutableMap<String, Any>

    @Before
    fun setup() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        templateModel = mutableMapOf()
        templateModel["firstName"] = "Luke"
        templateModel["lastName"] = "Skywalker"
        templateModel["age"] = "25"

        val notificationTemplate = Json.mapper.convert(stringJsonFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(notificationTemplate)
    }

    @After
    fun clear() {
        greenMail.stop()
        failureNotificationRepository.deleteAll()
    }

    @Test
    fun sendNotificationWithPotentialErrorShouldSaveFailureNotification() {
        val command = SendNotificationCommand(
            record = null,
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "some_fail",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val allFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)

        assertThat(result!!.status).isEqualTo("error")
        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allFailures[0].errorMessage).contains("Invalid locale format")
    }

    @Test
    fun sendNotificationWithoutMailServerShouldSaveFailureNotification() {
        greenMail.stop()

        val command = SendNotificationCommand(
            record = null,
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        val allFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)

        assertThat(result!!.status).isEqualTo("error")
        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allFailures[0].errorMessage).contains("Mail server connection failed")
    }

}
