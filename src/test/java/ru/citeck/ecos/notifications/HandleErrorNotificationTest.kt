package ru.citeck.ecos.notifications

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.domain.notification.service.NotificationRetryMetrics
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Verifies the retry semantics of the rewritten [ErrorNotificationRepeater]:
 * one tick claims a bounded batch of due ERROR rows and routes every outcome
 * through the shared retry policy (transient -> retried, permanent -> FAILED,
 * budget exhausted -> EXPIRED). Test config (application-test.yml):
 * max-attempts=3, retry-window=30s, initial-interval=1ms.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class HandleErrorNotificationTest : BaseMailTest() {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var props: ApplicationProperties

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @AfterEach
    fun clear() {
        notificationRepository.deleteAll()
    }

    @Test
    fun transientFailureIsRetriedAndSent() {
        greenMail.stop()

        val command = buildCommand()
        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.ERROR.value)

        val failedRow = notificationRepository.findOneByExtId(command.id).get()
        assertThat(failedRow.state).isEqualTo(NotificationState.ERROR)
        assertThat(failedRow.failureKind).isEqualTo(FailureKind.TRANSIENT)
        assertThat(failedRow.tryingCount).isEqualTo(1)
        assertThat(failedRow.firstErrorAt).isNotNull()
        assertThat(failedRow.nextRetryAt).isNotNull()

        greenMail.start()
        errorNotificationRepeater.handleErrors()

        val sentRow = notificationRepository.findOneByExtId(command.id).get()
        assertThat(sentRow.state).isEqualTo(NotificationState.SENT)
        assertThat(sentRow.tryingCount).isEqualTo(2)
        assertThat(sentRow.nextRetryAt).isNull()
        // the retry-window anchor must not survive a success, otherwise a later failure of the
        // same command id would be measured against the old first error and expire at once
        assertThat(sentRow.firstErrorAt).isNull()
        assertThat(sentRow.failureKind).isNull()
        assertThat(sentRow.errorMessage).isEmpty()

        assertThat(greenMail.receivedMessages).hasSize(1)
    }

    /**
     * The scenario that motivated the redesign: a permanently broken notification used to be
     * re-sent ~145 times over 24h. It must now cost exactly one attempt and stay FAILED,
     * however many ticks run afterwards.
     */
    @Test
    fun permanentFailureIsAttemptedExactlyOnce() {
        val command = buildCommand(templateId = "nonexistent-template-for-spam-check")

        commandsService.executeSync(command, "notifications")

        var row = notificationRepository.findOneByExtId(command.id).get()
        assertThat(row.state).isEqualTo(NotificationState.FAILED)
        assertThat(row.tryingCount).isEqualTo(1)

        repeat(3) { errorNotificationRepeater.handleErrors() }

        row = notificationRepository.findOneByExtId(command.id).get()
        assertThat(row.state).isEqualTo(NotificationState.FAILED)
        assertThat(row.tryingCount).isEqualTo(1)
        assertThat(row.nextRetryAt).isNull()
    }

    /**
     * With retries switched off the first transient failure is terminal at once (EXPIRED),
     * and no tick ever picks it up again — manual re-drive stays the only way forward.
     */
    @Test
    fun retryDisabledSendsFirstTransientFailureStraightToTerminalState() {
        greenMail.stop()

        val command = buildCommand()

        props.retry.isEnabled = false
        try {
            commandsService.executeSync(command, "notifications")

            val row = notificationRepository.findOneByExtId(command.id).get()
            assertThat(row.state).isEqualTo(NotificationState.EXPIRED)
            assertThat(row.failureKind).isEqualTo(FailureKind.TRANSIENT)
            assertThat(row.tryingCount).isEqualTo(1)
            assertThat(row.nextRetryAt).isNull()

            errorNotificationRepeater.scheduledRetryTick()

            val untouched = notificationRepository.findOneByExtId(command.id).get()
            assertThat(untouched.state).isEqualTo(NotificationState.EXPIRED)
            assertThat(untouched.tryingCount).isEqualTo(1)
        } finally {
            props.retry.isEnabled = true
        }
    }

    @Test
    fun permanentFailureOnRetryGoesFailed() {
        val command = buildCommand(templateId = "nonexistent-template-for-retry")
        val row = errorRow(data = Json.mapper.toBytes(command), tryingCount = 1)

        errorNotificationRepeater.handleErrors()

        val updated = notificationRepository.findById(row.id!!).get()
        assertThat(updated.state).isEqualTo(NotificationState.FAILED)
        assertThat(updated.failureKind).isEqualTo(FailureKind.PERMANENT)
        assertThat(updated.nextRetryAt).isNull()
        assertThat(updated.tryingCount).isEqualTo(2)
    }

    @Test
    fun attemptsBudgetExhaustedGoesExpired() {
        greenMail.stop()

        // max-attempts=3: the failed attempt below is the third one
        val row = errorRow(data = Json.mapper.toBytes(buildCommand()), tryingCount = 2)

        errorNotificationRepeater.handleErrors()

        val updated = notificationRepository.findById(row.id!!).get()
        assertThat(updated.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(updated.tryingCount).isEqualTo(3)
        assertThat(updated.nextRetryAt).isNull()
    }

    @Test
    fun retryWindowExceededGoesExpired() {
        greenMail.stop()

        // retry-window=30s, first error a minute ago
        val row = errorRow(
            data = Json.mapper.toBytes(buildCommand()),
            firstErrorAt = Instant.now().minus(1, ChronoUnit.MINUTES)
        )

        errorNotificationRepeater.handleErrors()

        val updated = notificationRepository.findById(row.id!!).get()
        assertThat(updated.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(updated.tryingCount).isEqualTo(1)
        assertThat(updated.nextRetryAt).isNull()
    }

    @Test
    fun rowScheduledInTheFutureIsNotPickedUp() {
        // truncated to the precision a postgres "timestamp" keeps: Instant.now() carries
        // nanoseconds on linux, so an untruncated value would not survive the round trip
        val nextRetryAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS)
        val row = errorRow(nextRetryAt = nextRetryAt)

        errorNotificationRepeater.handleErrors()

        val updated = notificationRepository.findById(row.id!!).get()
        assertThat(updated.state).isEqualTo(NotificationState.ERROR)
        assertThat(updated.tryingCount).isEqualTo(0)
        assertThat(updated.nextRetryAt).isEqualTo(nextRetryAt)
    }

    @Test
    fun tickProcessesAtMostBatchSizeRows() {
        val originalBatchSize = props.retry.batchSize
        props.retry.batchSize = 2
        try {
            // rows without data fail to deserialize -> transient failure -> tryingCount + 1
            repeat(3) { errorRow() }

            errorNotificationRepeater.handleErrors()

            val rows = notificationRepository.findAll()
            assertThat(rows.count { it.tryingCount == 1 }).isEqualTo(2)
            assertThat(rows.count { it.tryingCount == 0 }).isEqualTo(1)
        } finally {
            props.retry.batchSize = originalBatchSize
        }
    }

    /**
     * With retries off the policy never schedules a row, so the only rows the tick can find are
     * the ones an admin re-drove by hand. They must still be drained — for exactly one attempt,
     * after which they go terminal. This is the documented "re-drive still works" guarantee.
     */
    @Test
    fun scheduledTickStillDrainsRedrivenRowsWhenRetryDisabled() {
        // a re-driven row: fresh budget, due now (no data -> the attempt fails transiently)
        val row = errorRow(tryingCount = 0, firstErrorAt = null)

        props.retry.isEnabled = false
        try {
            errorNotificationRepeater.scheduledRetryTick()

            val drained = notificationRepository.findById(row.id!!).get()
            assertThat(drained.tryingCount).isEqualTo(1)
            assertThat(drained.state).isEqualTo(NotificationState.EXPIRED)
            assertThat(drained.nextRetryAt).isNull()

            // terminal now, so further ticks leave it alone
            errorNotificationRepeater.scheduledRetryTick()
            assertThat(notificationRepository.findById(row.id!!).get().tryingCount).isEqualTo(1)
        } finally {
            props.retry.isEnabled = true
        }
    }

    /**
     * The kill switch must also stop the backlog that was scheduled while retries were still on:
     * such a row is claimed, but expired without sending anything. Otherwise flipping the switch
     * during a mail storm would still let every scheduled row out once.
     */
    @Test
    fun rowScheduledBeforeTheSwitchIsExpiredWithoutSendingWhenRetryDisabled() {
        // a real command with a working SMTP: any attempt would produce a message
        val row = errorRow(data = Json.mapper.toBytes(buildCommand()), tryingCount = 1)

        props.retry.isEnabled = false
        try {
            errorNotificationRepeater.scheduledRetryTick()

            val expired = notificationRepository.findById(row.id!!).get()
            assertThat(expired.state).isEqualTo(NotificationState.EXPIRED)
            assertThat(expired.nextRetryAt).isNull()
            // no attempt was made, so the counter of the last real attempt is kept as is
            assertThat(expired.tryingCount).isEqualTo(1)
            assertThat(greenMail.receivedMessages).isEmpty()
        } finally {
            props.retry.isEnabled = true
        }
    }

    /**
     * Verifies that the metrics are actually wired into the repeater — the counter semantics
     * themselves are covered by NotificationRetryMetricsTest. Counters are context-wide, so
     * every assertion is a delta.
     */
    @Test
    fun retryMetricsAreRecorded() {
        val errorAttemptsBefore = attemptsCount(NotificationRetryMetrics.OUTCOME_ERROR)
        val sentAttemptsBefore = attemptsCount(NotificationRetryMetrics.OUTCOME_SENT)
        val failedTerminalBefore = terminalCount("failed")

        errorRow(
            data = Json.mapper.toBytes(buildCommand(templateId = "nonexistent-template-for-metrics")),
            tryingCount = 1
        )
        errorNotificationRepeater.handleErrors()

        assertThat(attemptsCount(NotificationRetryMetrics.OUTCOME_ERROR)).isEqualTo(errorAttemptsBefore + 1)
        assertThat(terminalCount("failed")).isEqualTo(failedTerminalBefore + 1)

        errorRow(data = Json.mapper.toBytes(buildCommand()))
        errorNotificationRepeater.handleErrors()

        assertThat(attemptsCount(NotificationRetryMetrics.OUTCOME_SENT)).isEqualTo(sentAttemptsBefore + 1)
    }

    private fun attemptsCount(outcome: String): Double {
        return meterRegistry.get(NotificationRetryMetrics.ATTEMPTS_METRIC)
            .tag(NotificationRetryMetrics.OUTCOME_TAG, outcome)
            .counter().count()
    }

    private fun terminalCount(state: String): Double {
        return meterRegistry.get(NotificationRetryMetrics.TERMINAL_METRIC)
            .tag(NotificationRetryMetrics.STATE_TAG, state)
            .counter().count()
    }

    @Test
    fun sendRecipientsNotFound() {
        var allRecipientsNotFound = notificationRepository.findAllByState(NotificationState.RECIPIENTS_NOT_FOUND)
        val allSent = notificationRepository.findAllByState(NotificationState.SENT)

        assertThat(allRecipientsNotFound.size).isEqualTo(0)
        assertThat(allSent.size).isEqualTo(0)

        val command = buildCommand(recipients = setOf())

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo(NotificationResultStatus.RECIPIENTS_NOT_FOUND.value)

        allRecipientsNotFound = notificationRepository.findAllByState(NotificationState.RECIPIENTS_NOT_FOUND)
        assertThat(allRecipientsNotFound.size).isEqualTo(1)
    }

    private fun buildCommand(
        templateId: String = "test-template",
        recipients: Set<String> = setOf("someUser@gmail.com")
    ): SendNotificationCommand {
        return SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = EntityRef.EMPTY,
            templateRef = EntityRef.create("notifications", "template", templateId),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = recipients,
            model = templateModel,
            from = "testFrom@mail.ru"
        )
    }

    private fun errorRow(
        data: ByteArray? = null,
        tryingCount: Int = 0,
        nextRetryAt: Instant = Instant.now().minus(1, ChronoUnit.MINUTES),
        firstErrorAt: Instant? = Instant.now().minusSeconds(5)
    ): NotificationEntity {
        return notificationRepository.save(
            NotificationEntity(
                extId = UUID.randomUUID().toString(),
                state = NotificationState.ERROR,
                tryingCount = tryingCount,
                data = data,
                nextRetryAt = nextRetryAt,
                firstErrorAt = firstErrorAt,
                failureKind = FailureKind.TRANSIENT
            )
        )
    }
}
