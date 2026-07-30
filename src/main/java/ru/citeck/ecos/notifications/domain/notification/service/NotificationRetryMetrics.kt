package ru.citeck.ecos.notifications.domain.notification.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Micrometer instrumentation of the retry pipeline:
 *
 * - `notifications.retry.attempts` (counter, tag `outcome` = `sent` / `error`) — result of every
 *   retry attempt made by [ErrorNotificationRepeater]
 * - `notifications.retry.terminal` (counter, tag `state` = `failed` / `expired`) — transitions
 *   into a terminal state, counted wherever they happen: the repeater and the synchronous error
 *   path ([NotificationCommandResultHolder]), each transition exactly once
 * - `notifications.retry.backlog` (gauge) — rows still waiting for a retry
 *
 * All counters are registered eagerly so they are exported as zero before the first failure.
 * The backlog gauge is polled on scrape, so its value is cached for [BACKLOG_CACHE_TTL] to keep
 * frequent scrapes from hitting the database; a failed count keeps the previous value instead of
 * reporting a misleading zero, and is cached for the same TTL so a database outage is not
 * re-queried (and re-logged) on every scrape.
 */
@Component
class NotificationRetryMetrics(
    meterRegistry: MeterRegistry,
    private val notificationDao: NotificationDao
) {

    companion object {
        const val ATTEMPTS_METRIC = "notifications.retry.attempts"
        const val TERMINAL_METRIC = "notifications.retry.terminal"
        const val BACKLOG_METRIC = "notifications.retry.backlog"

        const val OUTCOME_TAG = "outcome"
        const val OUTCOME_SENT = "sent"
        const val OUTCOME_ERROR = "error"

        const val STATE_TAG = "state"

        val BACKLOG_CACHE_TTL: Duration = Duration.ofSeconds(15)

        private val TERMINAL_STATES = setOf(NotificationState.FAILED, NotificationState.EXPIRED)

        private val log = KotlinLogging.logger {}
    }

    private val attemptSent = attemptCounter(meterRegistry, OUTCOME_SENT)
    private val attemptError = attemptCounter(meterRegistry, OUTCOME_ERROR)

    private val terminalCounters = TERMINAL_STATES.associateWith { state ->
        Counter.builder(TERMINAL_METRIC)
            .description("Notifications moved to a terminal state by the retry pipeline")
            .tag(STATE_TAG, state.name.lowercase())
            .register(meterRegistry)
    }

    private val backlogCache = AtomicReference<Pair<Instant, Long>?>(null)

    init {
        Gauge.builder(BACKLOG_METRIC) { getBacklog() }
            .description("Notifications waiting for another retry attempt")
            .register(meterRegistry)
    }

    /**
     * @param resultState state the notification ended up in after a retry attempt: any of the
     * non-delivered states means the attempt itself failed.
     */
    fun recordRetryAttempt(resultState: NotificationState) {
        if (resultState == NotificationState.ERROR || resultState in TERMINAL_STATES) {
            attemptError.increment()
        } else {
            attemptSent.increment()
        }
    }

    /**
     * Counts a transition into FAILED/EXPIRED. Non-terminal states are ignored, so callers can
     * pass the resulting state unconditionally.
     */
    fun recordTerminalState(state: NotificationState) {
        terminalCounters[state]?.increment()
    }

    fun getBacklog(): Long {
        val now = Instant.now()
        val cached = backlogCache.get()
        if (cached != null && Duration.between(cached.first, now) < BACKLOG_CACHE_TTL) {
            return cached.second
        }
        val counted = try {
            notificationDao.getErrorBacklogCount()
        } catch (e: Exception) {
            // the failure is cached like a value: the gauge is polled on every scrape, so without
            // it a database outage would mean one failing query and one WARN per scrape
            val previous = cached?.second ?: 0L
            backlogCache.set(now to previous)
            log.warn(e) { "Failed to count retry backlog, keeping the previous gauge value $previous" }
            return previous
        }
        backlogCache.set(now to counted)
        return counted
    }

    private fun attemptCounter(meterRegistry: MeterRegistry, outcome: String): Counter {
        return Counter.builder(ATTEMPTS_METRIC)
            .description("Retry attempts performed for notifications in the ERROR state")
            .tag(OUTCOME_TAG, outcome)
            .register(meterRegistry)
    }
}
