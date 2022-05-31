package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef
import java.time.Duration
import java.util.*

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class HandleErrorNotificationTest : BaseMailTest() {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    @Autowired
    private lateinit var commandsService: CommandsService

    private lateinit var activeFailure: NotificationEntity
    private lateinit var disabledFailure: NotificationEntity

    @Before
    fun setup() {
        notificationRepository.deleteAll()

        activeFailure = notificationRepository.save(
            NotificationEntity(
                tryingCount = 0,
                state = NotificationState.ERROR
            )
        )

        disabledFailure = notificationRepository.save(
            NotificationEntity(
                tryingCount = 3,
                state = NotificationState.EXPIRED
            )
        )
    }

    @After
    fun clear() {
        notificationRepository.deleteAll()
    }

    @Test
    fun processFailuresMustIncrementTryingCount() {
        errorNotificationRepeater.handleErrors()

        val updatedFailure = notificationRepository.findById(activeFailure.id!!).get()

        assertThat(updatedFailure.tryingCount).isEqualTo(1)
    }

    @Test
    fun processFailuresMustIncrementTryingCountMultiple() {
        errorNotificationRepeater.handleErrors()
        errorNotificationRepeater.handleErrors()
        errorNotificationRepeater.handleErrors()

        val updatedFailure = notificationRepository.findById(activeFailure.id!!).get()

        assertThat(updatedFailure.tryingCount).isEqualTo(3)
    }

    @Test
    fun processFailuresMustSetLastTryingDate() {
        errorNotificationRepeater.handleErrors()

        val updatedFailure = notificationRepository.findById(activeFailure.id!!).get()

        assertThat(updatedFailure.lastTryingDate).isNotNull()
    }

    @Test
    fun processFailureWithSuccessfulHandle() {
        var allFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        var allSent = notificationRepository.findAllByState(NotificationState.SENT)
        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(0)

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

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        allFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        assertThat(allFailures.size).isEqualTo(2)

        greenMail.start()
        errorNotificationRepeater.handleErrors()

        allFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        allSent = notificationRepository.findAllByState(NotificationState.SENT)

        assertThat(allFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(1)
    }

    @Test
    fun processFailureWithSuccessfulHandleByJob() {
        var errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        var allSent = notificationRepository.findAllByState(NotificationState.SENT)
        assertThat(errorFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(0)

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

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        assertThat(errorFailures.size).isEqualTo(2)

        greenMail.start()

        await().atMost(Duration.ofSeconds(20)).untilAsserted {

            errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
            allSent = notificationRepository.findAllByState(NotificationState.SENT)

            assertThat(errorFailures.size).isEqualTo(1)
            assertThat(allSent.size).isEqualTo(1)
        }
    }

    @Test
    fun processFailureExpired() {
        var errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        var allSent = notificationRepository.findAllByState(NotificationState.SENT)
        var allExpired = notificationRepository.findAllByState(NotificationState.EXPIRED)
        assertThat(errorFailures.size).isEqualTo(1)
        assertThat(allSent.size).isEqualTo(0)
        assertThat(allExpired.size).isEqualTo(1)

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

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        assertThat(errorFailures.size).isEqualTo(2)

        await().atMost(Duration.ofSeconds(40)).untilAsserted {

            errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
            allExpired = notificationRepository.findAllByState(NotificationState.EXPIRED)
            allSent = notificationRepository.findAllByState(NotificationState.SENT)

            assertThat(errorFailures.size).isEqualTo(0)
            assertThat(allSent.size).isEqualTo(0)
            assertThat(allExpired.size).isEqualTo(3)
        }
    }

}
