package ru.citeck.ecos.notifications.domain.notification.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.api.commands.UnsafeSendNotificationCommandExecutor
import ru.citeck.ecos.notifications.domain.notification.converter.toNotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import java.time.Instant

/**
 * Retry job for notifications in the ERROR state.
 *
 * One tick claims at most `retry.batch-size` due rows ([NotificationDao.claimErrorsForRetry],
 * FOR UPDATE SKIP LOCKED + lease) — bounded work per tick, leftover backlog waits for the next
 * tick, which throttles the recovery drain after an outage. The lease keeps other replicas away
 * from claimed rows; sending happens outside any transaction, and results are persisted with
 * [NotificationDao.saveIfStateStillError] so rows cancelled or re-driven mid-flight are never
 * overwritten. Retry/terminal decisions are delegated to [NotificationRetryPolicy] — the same
 * code path as the synchronous error path in [NotificationCommandResultHolder].
 */
@Component
class ErrorNotificationRepeater(
    private val notificationDao: NotificationDao,
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val failureClassifier: NotificationFailureClassifier,
    private val retryPolicy: NotificationRetryPolicy,
    private val metrics: NotificationRetryMetrics,
    private val props: ApplicationProperties
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(
        initialDelayString = "\${ecos-notifications.retry.poll-interval}",
        fixedDelayString = "\${ecos-notifications.retry.poll-interval}"
    )
    fun scheduledRetryTick() {
        handleErrors()
    }

    /**
     * The tick runs even with `retry.enabled = false`: with retries switched off
     * [NotificationRetryPolicy] never schedules a row, so the only rows the tick may still send are
     * the ones an admin re-drove by hand — and each of them gets exactly one attempt before going
     * terminal. That is what keeps the documented "re-drive still works while retries are off"
     * guarantee. Rows scheduled before the switch was flipped are claimed too, but the pre-send gate
     * ([NotificationRetryPolicy.shouldAttempt]) expires them without sending anything.
     */
    fun handleErrors() {
        val leaseDeadline = Instant.now().plus(props.retry.leaseTime)
        val claimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(props.retry.batchSize, props.retry.leaseTime)
        }
        if (claimed.isEmpty()) {
            return
        }

        log.info { "Claimed ${claimed.size} error notifications for retry" }

        AuthContext.runAsSystem {
            for ((index, notification) in claimed.withIndex()) {
                // a slow SMTP server can stretch a batch past its own lease; the remaining rows
                // still hold a claim, so they are simply left for the next tick after it expires
                if (Instant.now() >= leaseDeadline) {
                    log.warn {
                        "Retry lease expired after $index of ${claimed.size} notifications, " +
                            "the rest is left for the next tick"
                    }
                    break
                }
                try {
                    retry(notification)
                } catch (e: Exception) {
                    // a row that fails to persist its outcome must not cost the rest of the batch
                    // its tick - those rows would stay claimed until their lease expires
                    log.error("Failed to process retry of notification with id ${notification.id}", e)
                }
            }
        }
    }

    private fun retry(notification: NotificationDto) {
        if (!retryPolicy.shouldAttempt(notification)) {
            expireWithoutAttempt(notification)
            return
        }

        log.debug {
            "Retry notification with id ${notification.id}. " +
                "Current trying count: ${notification.tryingCount}"
        }

        val now = Instant.now()
        val updated = try {
            val command = Json.mapper.read(notification.data, SendNotificationCommand::class.java)
                ?: throw IllegalStateException(
                    "Failed convert notification data to command. Notification: $notification"
                )

            val result = unsafeSendNotificationCommandExecutor.execute(command)

            // the row leaves the retry pipeline: clear the schedule, the failure verdict and the
            // retry-window anchor (a stale firstErrorAt would expire a later failure at once).
            // A partial delivery counts as success too - its note replaces the error message
            notification.copy(
                state = result.commandResult.toNotificationState(),
                tryingCount = notification.tryingCount.plus(1),
                lastTryingDate = now,
                errorMessage = result.partialDeliveryNote ?: "",
                errorStackTrace = "",
                nextRetryAt = null,
                firstErrorAt = null,
                failureKind = null
            )
        } catch (e: Exception) {
            log.error("Failed reexecute notification command. Notification id: ${notification.id}", e)

            val errorMessage = ExceptionUtils.getMessage(e)
            val base = notification.copy(
                errorMessage = errorMessage,
                // identical multi-KB traces are not rewritten on every attempt
                errorStackTrace = if (notification.errorMessage == errorMessage) {
                    notification.errorStackTrace
                } else {
                    ExceptionUtils.getStackTrace(e)
                }
            )
            retryPolicy.applyFailure(base, failureClassifier.classify(e), now)
        }

        // the claimed lease deadline is the claim token: if the row was cancelled, re-driven or
        // re-claimed by another replica meanwhile, the result of this attempt must not land
        if (!notificationDao.saveIfStateStillError(updated, notification.nextRetryAt)) {
            log.warn {
                "Notification ${notification.id} left the retry claim concurrently, retry result is dropped"
            }
            return
        }

        metrics.recordRetryAttempt(updated.state)
        metrics.recordTerminalState(updated.state)

        when (updated.state) {
            NotificationState.ERROR -> log.debug {
                "Notification ${notification.id} failed again, next attempt at ${updated.nextRetryAt}"
            }
            NotificationState.FAILED, NotificationState.EXPIRED -> log.warn {
                "Notification ${notification.id} moved to terminal state ${updated.state} " +
                    "after ${updated.tryingCount} attempts"
            }
            else -> log.info {
                "Successful re executing notification command. " +
                    "Notification id: ${notification.id}, state: ${updated.state}"
            }
        }
    }

    /**
     * Closes a row the policy refuses to attempt (retries were switched off after it had already
     * been scheduled): no send happens, the row goes straight to EXPIRED under the same claim token.
     */
    private fun expireWithoutAttempt(notification: NotificationDto) {
        val expired = retryPolicy.expireWithoutAttempt(notification)
        if (!notificationDao.saveIfStateStillError(expired, notification.nextRetryAt)) {
            log.warn {
                "Notification ${notification.id} left the retry claim concurrently, expiration is dropped"
            }
            return
        }

        metrics.recordTerminalState(expired.state)

        log.warn {
            "Notification ${notification.id} expired without an attempt after " +
                "${notification.tryingCount} attempts: retries are disabled"
        }
    }
}
