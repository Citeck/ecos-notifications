package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.FailureNotificationService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef
import java.time.Duration

@RunWith(SpringRunner::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(classes = [NotificationsApp::class])
class HandleFailureNotificationTest {

    @Autowired
    private lateinit var failureNotificationRepository: FailureNotificationRepository

    @Autowired
    private lateinit var failureNotificationService: FailureNotificationService

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    private lateinit var activeFailure: FailureNotificationEntity
    private lateinit var disabledFailure: FailureNotificationEntity

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


        activeFailure = failureNotificationRepository.save(FailureNotificationEntity(
            tryingCount = 0,
            state = FailureNotificationState.ERROR
        ))

        disabledFailure = failureNotificationRepository.save(FailureNotificationEntity(
            tryingCount = 3,
            state = FailureNotificationState.EXPIRED
        ))

    }

    @After
    fun clear() {
        greenMail.stop()
        failureNotificationRepository.deleteAll()
    }


    @Test
    fun processFailuresMustIncrementTryingCount() {
        failureNotificationService.handleErrors()

        val updatedFailure = failureNotificationRepository.findById(activeFailure.id!!).get()

        assertThat(updatedFailure.tryingCount).isEqualTo(1)
    }

    @Test
    fun processFailuresMustIncrementTryingCountMultiple() {
        failureNotificationService.handleErrors()
        failureNotificationService.handleErrors()
        failureNotificationService.handleErrors()

        val updatedFailure = failureNotificationRepository.findById(activeFailure.id!!).get()

        assertThat(updatedFailure.tryingCount).isEqualTo(3)
    }

    @Test
    fun processFailuresMustSetLastTryingDate() {
        failureNotificationService.handleErrors()

        val updatedFailure = failureNotificationRepository.findById(activeFailure.id!!).get()

        assertThat(updatedFailure.lastTryingDate).isNotNull()
    }

    @Test
    fun processFailureWithSuccessfulHandle() {
        var allFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        var allSent = failureNotificationRepository.findAllByState(FailureNotificationState.SENT)
        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(0)

        greenMail.stop()

        val command = SendNotificationCommand(
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

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        allFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        assertThat(allFailures.size).isEqualTo(2)

        greenMail.start()
        failureNotificationService.handleErrors()

        allFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        allSent = failureNotificationRepository.findAllByState(FailureNotificationState.SENT)

        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(1)
    }

    @Test
    fun processFailureWithSuccessfulHandleByJob() {
        var errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        var allSent = failureNotificationRepository.findAllByState(FailureNotificationState.SENT)
        assertThat(errorFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(0)

        greenMail.stop()

        val command = SendNotificationCommand(
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

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        assertThat(errorFailures.size).isEqualTo(2)

        greenMail.start()

        await().atMost(Duration.ofSeconds(20)).untilAsserted {

            errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
            allSent = failureNotificationRepository.findAllByState(FailureNotificationState.SENT)

            assertThat(errorFailures.size).isEqualTo(1)
            assertThat(allSent.size).isEqualTo(1)
        }
    }

    @Test
    fun processFailureExpired() {
        var errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        var allSent = failureNotificationRepository.findAllByState(FailureNotificationState.SENT)
        var allExpired = failureNotificationRepository.findAllByState(FailureNotificationState.EXPIRED)
        assertThat(errorFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(0)
        assertThat(allExpired.size).isEqualTo(1)

        greenMail.stop()

        val command = SendNotificationCommand(
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

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        assertThat(errorFailures.size).isEqualTo(2)

        await().atMost(Duration.ofSeconds(40)).untilAsserted {

            errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
            allExpired = failureNotificationRepository.findAllByState(FailureNotificationState.EXPIRED)
            allSent = failureNotificationRepository.findAllByState(FailureNotificationState.SENT)

            assertThat(errorFailures.size).isEqualTo(0)
            assertThat(allSent.size).isEqualTo(0)
            assertThat(allExpired.size).isEqualTo(3)
        }
    }

}
