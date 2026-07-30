package ru.citeck.ecos.notifications.domain.notification.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min
import kotlin.math.pow

/**
 * The single decision point for what happens to a notification after a failed send attempt.
 * Both the synchronous error path ([NotificationCommandResultHolder]) and the retry job
 * ([ErrorNotificationRepeater]) must go through this policy — retry rules live only here.
 *
 * Pure logic: no persistence, no clock — `now` is passed in by the caller.
 *
 * Contract: [applyFailure] receives the notification as currently persisted, i.e. with
 * `tryingCount` NOT yet reflecting the attempt that just failed. The returned copy counts
 * that attempt (`tryingCount + 1`), stamps `lastTryingDate = now`, fills `firstErrorAt`
 * (only if not set yet) and `failureKind`. A terminal verdict (EXPIRED/FAILED) is made in
 * the same computation — no extra tick is needed to expire a row and, unlike the legacy
 * repeater, expiring itself never adds a phantom `tryingCount` increment.
 *
 * Decision table (first matching row wins; `attempt = tryingCount + 1`):
 *
 * | condition                                 | state   | nextRetryAt   | meaning                          |
 * |-------------------------------------------|---------|---------------|----------------------------------|
 * | failureKind = PERMANENT                   | FAILED  | null          | terminal, retrying is pointless  |
 * | retry.enabled = false (transient failure) | EXPIRED | null          | terminal, retries switched off   |
 * | attempt >= retry.maxAttempts              | EXPIRED | null          | terminal, attempts ceiling hit   |
 * | now - firstErrorAt > retry.retryWindow    | EXPIRED | null          | terminal, retry window elapsed   |
 * | otherwise                                 | ERROR   | now + backoff | non-terminal, attempt scheduled  |
 *
 * Besides the post-failure decision the policy also owns the pre-send gate ([shouldAttempt]):
 * `retry.enabled = false` is a kill switch, so it must stop the backlog that was already scheduled
 * while retries were on, not only prevent new scheduling.
 *
 * Notes on the table:
 * - PERMANENT wins over everything, `retry.enabled` included: a permanent failure is FAILED, never
 *   EXPIRED, so the terminal state always says WHY the notification stopped.
 * - Attempts and window are independent limits — whichever is reached first ends the retries. With
 *   production defaults the window (24h) fires long before the attempts ceiling (20).
 * - Fields the policy always fills: `tryingCount` (+1), `lastTryingDate`, `failureKind`, and
 *   `firstErrorAt` — the latter only when it is still null, so the window is measured from the
 *   FIRST failure of this notification, not from the last one.
 * - `nextRetryAt` is cleared on every terminal decision; nothing must be left scheduled.
 * - Terminal rows are only ever re-entered through the manual re-drive action
 *   (`NotificationRecords` `RETRY`), which resets `tryingCount`/`firstErrorAt`.
 *
 * Backoff: `min(initialInterval * multiplier^(attempt - 1), maxInterval)` with a uniform
 * ±20% jitter to avoid synchronized retry bursts after an outage. With production defaults
 * (1m, x3, cap 2h) the attempts land at roughly +1m, +4m, +13m, +40m, then every 2h.
 */
@Component
class NotificationRetryPolicy(
    private val props: ApplicationProperties
) {

    companion object {
        private const val JITTER_SPREAD = 0.2
    }

    /**
     * Pre-send gate for a row claimed by [ErrorNotificationRepeater]: may this attempt happen at all?
     *
     * With `retry.enabled = false` a row that already spent an attempt is never sent again — flipping
     * the switch during a mail storm has to stop the rows scheduled before the flip too, otherwise the
     * kill switch would still let the whole backlog out once. A manually re-driven row
     * ([NotificationDao.redriveForRetry] resets `tryingCount` to 0) is always attempted: that is the
     * documented "re-drive still works while retries are off" guarantee.
     */
    fun shouldAttempt(dto: NotificationDto): Boolean {
        return props.retry.isEnabled || dto.tryingCount == 0
    }

    /**
     * Terminal verdict for a row [shouldAttempt] refused. Nothing was sent, so `tryingCount`,
     * `lastTryingDate` and the failure verdict keep the values of the last real attempt.
     */
    fun expireWithoutAttempt(dto: NotificationDto): NotificationDto {
        return dto.copy(state = NotificationState.EXPIRED, nextRetryAt = null)
    }

    fun applyFailure(dto: NotificationDto, failureKind: FailureKind, now: Instant): NotificationDto {
        val retry = props.retry
        val attempt = dto.tryingCount + 1
        val firstErrorAt = dto.firstErrorAt ?: now

        val base = dto.copy(
            tryingCount = attempt,
            lastTryingDate = now,
            firstErrorAt = firstErrorAt,
            failureKind = failureKind
        )

        if (failureKind == FailureKind.PERMANENT) {
            return base.copy(state = NotificationState.FAILED, nextRetryAt = null)
        }

        val budgetExhausted = attempt >= retry.maxAttempts ||
            Duration.between(firstErrorAt, now) > retry.retryWindow

        if (!retry.isEnabled || budgetExhausted) {
            return base.copy(state = NotificationState.EXPIRED, nextRetryAt = null)
        }

        return base.copy(
            state = NotificationState.ERROR,
            nextRetryAt = now.plusMillis(nextDelayMillis(attempt, retry))
        )
    }

    private fun nextDelayMillis(attempt: Int, retry: ApplicationProperties.Retry): Long {
        val baseMs = retry.initialInterval.toMillis().toDouble() * retry.multiplier.pow(attempt - 1)
        val cappedMs = min(baseMs, retry.maxInterval.toMillis().toDouble())
        val jitter = 1.0 + ThreadLocalRandom.current().nextDouble(-JITTER_SPREAD, JITTER_SPREAD)
        return (cappedMs * jitter).toLong()
    }
}
