package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Verifies the manual re-drive action (`RETRY`) of [NotificationRecords]: a terminal row
 * (FAILED/EXPIRED) or a scheduled ERROR row is pushed back into the retry pipeline with a
 * fresh budget, while states where re-drive makes no sense are rejected.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationRedriveTest : BaseMailTest() {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    @AfterEach
    fun clear() {
        notificationRepository.deleteAll()
    }

    @Test
    fun failedRowIsRedrivenAndRetriedByRepeater() {
        val command = buildCommand()
        val row = row(
            state = NotificationState.FAILED,
            data = Json.mapper.toBytes(command),
            tryingCount = 4,
            failureKind = FailureKind.PERMANENT
        )

        redrive(row)

        val redriven = notificationRepository.findById(row.id!!).get()
        assertThat(redriven.state).isEqualTo(NotificationState.ERROR)
        assertThat(redriven.tryingCount).isEqualTo(0)
        assertThat(redriven.firstErrorAt).isNull()
        assertThat(redriven.failureKind).isNull()
        assertThat(redriven.nextRetryAt).isNotNull().isBeforeOrEqualTo(Instant.now())

        errorNotificationRepeater.handleErrors()

        val retried = notificationRepository.findById(row.id!!).get()
        assertThat(retried.state).isEqualTo(NotificationState.SENT)
        assertThat(retried.tryingCount).isEqualTo(1)
        assertThat(retried.nextRetryAt).isNull()
        assertThat(greenMail.receivedMessages).hasSize(1)
    }

    @Test
    fun expiredRowIsRedriven() {
        val row = row(state = NotificationState.EXPIRED, tryingCount = 3)

        redrive(row)

        val redriven = notificationRepository.findById(row.id!!).get()
        assertThat(redriven.state).isEqualTo(NotificationState.ERROR)
        assertThat(redriven.tryingCount).isEqualTo(0)
    }

    @Test
    fun errorRowScheduledInTheFutureIsRedrivenToImmediateAttempt() {
        val row = row(
            state = NotificationState.ERROR,
            tryingCount = 2,
            nextRetryAt = Instant.now().plus(2, ChronoUnit.HOURS)
        )

        redrive(row)

        val redriven = notificationRepository.findById(row.id!!).get()
        assertThat(redriven.nextRetryAt).isBeforeOrEqualTo(Instant.now())

        errorNotificationRepeater.handleErrors()

        // no command data -> the attempt fails again, but it did happen right away
        assertThat(notificationRepository.findById(row.id!!).get().tryingCount).isEqualTo(1)
    }

    @Test
    fun redriveOfSentRowIsRejected() {
        val row = row(state = NotificationState.SENT, tryingCount = 1)

        // the message is asserted so the test fails if the mutation starts failing
        // for any reason other than the retryable-state guard
        assertThatThrownBy { redrive(row) }
            .hasStackTraceContaining("can't be retried")

        val untouched = notificationRepository.findById(row.id!!).get()
        assertThat(untouched.state).isEqualTo(NotificationState.SENT)
        assertThat(untouched.tryingCount).isEqualTo(1)
        assertThat(untouched.nextRetryAt).isNull()
    }

    @Test
    fun redriveOfNotRetryableStatesIsRejected() {
        listOf(
            NotificationState.CANCELLED,
            NotificationState.WAIT_FOR_DISPATCH,
            NotificationState.BLOCKED,
            NotificationState.RECIPIENTS_NOT_FOUND
        ).forEach { state ->
            val row = row(state = state)

            assertThatThrownBy { redrive(row) }
                .hasStackTraceContaining("can't be retried")
                .hasStackTraceContaining(state.name)

            assertThat(notificationRepository.findById(row.id!!).get().state).isEqualTo(state)
        }
    }

    private fun redrive(row: NotificationEntity) {
        AuthContext.runAsSystem {
            recordsService.mutate(
                EntityRef.create("notification", row.extId!!),
                mapOf("action" to "RETRY")
            )
        }
    }

    private fun buildCommand(): SendNotificationCommand {
        return SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = EntityRef.EMPTY,
            templateRef = EntityRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )
    }

    private fun row(
        state: NotificationState,
        data: ByteArray? = null,
        tryingCount: Int = 0,
        nextRetryAt: Instant? = null,
        failureKind: FailureKind? = null
    ): NotificationEntity {
        return notificationRepository.save(
            NotificationEntity(
                extId = UUID.randomUUID().toString(),
                state = state,
                tryingCount = tryingCount,
                data = data,
                nextRetryAt = nextRetryAt,
                firstErrorAt = Instant.now().minus(1, ChronoUnit.HOURS),
                failureKind = failureKind
            )
        )
    }
}
