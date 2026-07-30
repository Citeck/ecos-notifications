package ru.citeck.ecos.notifications.domain.notification.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.citeck.ecos.notifications.domain.notification.NotificationState

class NotificationRetryMetricsTest {

    private val registry: MeterRegistry = SimpleMeterRegistry()
    private val notificationDao: NotificationDao = mock()
    private val metrics = NotificationRetryMetrics(registry, notificationDao)

    private fun attempts(outcome: String) = registry.get(NotificationRetryMetrics.ATTEMPTS_METRIC)
        .tag(NotificationRetryMetrics.OUTCOME_TAG, outcome)
        .counter().count()

    private fun terminal(state: String) = registry.get(NotificationRetryMetrics.TERMINAL_METRIC)
        .tag(NotificationRetryMetrics.STATE_TAG, state)
        .counter().count()

    @Test
    fun `counters are registered at zero before any attempt`() {
        assertThat(attempts(NotificationRetryMetrics.OUTCOME_SENT)).isZero()
        assertThat(attempts(NotificationRetryMetrics.OUTCOME_ERROR)).isZero()
        assertThat(terminal("failed")).isZero()
        assertThat(terminal("expired")).isZero()
    }

    @Test
    fun `successful attempt increments sent outcome`() {
        metrics.recordRetryAttempt(NotificationState.SENT)

        assertThat(attempts(NotificationRetryMetrics.OUTCOME_SENT)).isEqualTo(1.0)
        assertThat(attempts(NotificationRetryMetrics.OUTCOME_ERROR)).isZero()
    }

    @Test
    fun `attempt that stays in error increments error outcome`() {
        metrics.recordRetryAttempt(NotificationState.ERROR)

        assertThat(attempts(NotificationRetryMetrics.OUTCOME_ERROR)).isEqualTo(1.0)
        assertThat(attempts(NotificationRetryMetrics.OUTCOME_SENT)).isZero()
    }

    @Test
    fun `attempt ending in a terminal state counts as a failed attempt`() {
        metrics.recordRetryAttempt(NotificationState.FAILED)
        metrics.recordRetryAttempt(NotificationState.EXPIRED)

        assertThat(attempts(NotificationRetryMetrics.OUTCOME_ERROR)).isEqualTo(2.0)
        assertThat(attempts(NotificationRetryMetrics.OUTCOME_SENT)).isZero()
    }

    @Test
    fun `non-delivered but successful result states count as sent`() {
        metrics.recordRetryAttempt(NotificationState.RECIPIENTS_NOT_FOUND)
        metrics.recordRetryAttempt(NotificationState.BLOCKED)

        assertThat(attempts(NotificationRetryMetrics.OUTCOME_SENT)).isEqualTo(2.0)
        assertThat(attempts(NotificationRetryMetrics.OUTCOME_ERROR)).isZero()
    }

    @Test
    fun `terminal transitions are counted per state`() {
        metrics.recordTerminalState(NotificationState.FAILED)
        metrics.recordTerminalState(NotificationState.EXPIRED)
        metrics.recordTerminalState(NotificationState.EXPIRED)

        assertThat(terminal("failed")).isEqualTo(1.0)
        assertThat(terminal("expired")).isEqualTo(2.0)
    }

    @Test
    fun `non-terminal states do not touch the terminal counter`() {
        metrics.recordTerminalState(NotificationState.ERROR)
        metrics.recordTerminalState(NotificationState.SENT)
        metrics.recordTerminalState(NotificationState.CANCELLED)

        assertThat(terminal("failed")).isZero()
        assertThat(terminal("expired")).isZero()
    }

    @Test
    fun `backlog gauge reports the counted error rows`() {
        whenever(notificationDao.getErrorBacklogCount()).thenReturn(7L)

        assertThat(registry.get(NotificationRetryMetrics.BACKLOG_METRIC).gauge().value()).isEqualTo(7.0)
    }

    @Test
    fun `backlog count is cached between scrapes`() {
        whenever(notificationDao.getErrorBacklogCount()).thenReturn(3L)

        assertThat(metrics.getBacklog()).isEqualTo(3L)
        assertThat(metrics.getBacklog()).isEqualTo(3L)

        verify(notificationDao, times(1)).getErrorBacklogCount()
    }

    @Test
    fun `failed backlog count does not blow up the gauge`() {
        whenever(notificationDao.getErrorBacklogCount()).thenThrow(RuntimeException("db is down"))

        assertThat(metrics.getBacklog()).isZero()
        assertThat(registry.get(NotificationRetryMetrics.BACKLOG_METRIC).gauge().value()).isZero()
    }

    /**
     * The gauge is polled on every scrape, so a database outage must not mean a failing query
     * (and a WARN) per scrape: the failed result is cached like a successful one.
     */
    @Test
    fun `failed backlog count is cached between scrapes`() {
        whenever(notificationDao.getErrorBacklogCount()).thenThrow(RuntimeException("db is down"))

        assertThat(metrics.getBacklog()).isZero()
        assertThat(metrics.getBacklog()).isZero()

        verify(notificationDao, times(1)).getErrorBacklogCount()
    }
}
