package ru.citeck.ecos.notifications.domain.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration
import java.time.Instant

class NotificationRetryPolicyTest {

    private val now: Instant = Instant.parse("2026-07-30T10:00:00Z")

    private fun policy(configure: ApplicationProperties.Retry.() -> Unit = {}): NotificationRetryPolicy {
        val props = ApplicationProperties()
        props.retry.configure()
        return NotificationRetryPolicy(props)
    }

    private fun dto(
        tryingCount: Int = 0,
        state: NotificationState = NotificationState.ERROR,
        firstErrorAt: Instant? = null
    ) = NotificationDto(
        extId = "test-notification",
        workspace = "",
        record = EntityRef.EMPTY,
        template = EntityRef.EMPTY,
        errorMessage = "boom",
        errorStackTrace = "",
        tryingCount = tryingCount,
        state = state,
        firstErrorAt = firstErrorAt
    )

    private fun delayOf(result: NotificationDto): Duration {
        assertThat(result.nextRetryAt).isNotNull
        return Duration.between(now, result.nextRetryAt)
    }

    private fun assertWithinJitter(delay: Duration, expected: Duration) {
        val expectedMs = expected.toMillis()
        assertThat(delay.toMillis())
            .isBetween((expectedMs * 0.8).toLong(), (expectedMs * 1.2).toLong())
    }

    @Test
    fun `backoff sequence grows exponentially and is capped at maxInterval`() {
        val policy = policy()
        // defaults: initialInterval=1m, multiplier=3.0, maxInterval=2h
        val expectedByAttempt = mapOf(
            1 to Duration.ofMinutes(1),
            2 to Duration.ofMinutes(3),
            3 to Duration.ofMinutes(9),
            4 to Duration.ofMinutes(27),
            5 to Duration.ofMinutes(81),
            6 to Duration.ofHours(2),
            10 to Duration.ofHours(2)
        )

        expectedByAttempt.forEach { (attempt, expected) ->
            val result = policy.applyFailure(dto(tryingCount = attempt - 1, firstErrorAt = now), FailureKind.TRANSIENT, now)
            assertThat(result.state).isEqualTo(NotificationState.ERROR)
            assertWithinJitter(delayOf(result), expected)
        }
    }

    /**
     * Acceptance criterion for the redesign: with production defaults a transient failure is
     * re-attempted at roughly +1m / +4m / +13m / +40m after the first error, then every 2 hours,
     * and the row expires shortly after the 24h retry window — spending the window, not the
     * attempts ceiling.
     */
    @Test
    fun `production defaults produce the documented retry schedule and expire near the window`() {
        val policy = policy()
        val expectedFirstOffsets = listOf(
            Duration.ofMinutes(1),
            Duration.ofMinutes(4),
            Duration.ofMinutes(13),
            Duration.ofMinutes(40)
        )

        val offsets = mutableListOf<Duration>()
        var current = dto()
        var clock = now
        var guard = 0
        do {
            current = policy.applyFailure(current, FailureKind.TRANSIENT, clock)
            if (current.state == NotificationState.ERROR) {
                clock = current.nextRetryAt!!
                offsets.add(Duration.between(now, clock))
            }
            // safety bound: the schedule must terminate on its own, well before this
            assertThat(guard++).isLessThan(100)
        } while (current.state == NotificationState.ERROR)

        expectedFirstOffsets.forEachIndexed { index, expected ->
            assertWithinJitter(offsets[index], expected)
        }
        // steady state: every subsequent attempt is one capped interval later
        val steadyStateStep = offsets.last().minus(offsets[offsets.size - 2])
        assertWithinJitter(steadyStateStep, Duration.ofHours(2))

        assertThat(current.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(current.nextRetryAt).isNull()
        // expiring is triggered by the window, not by max-attempts (20 by default)
        assertThat(current.tryingCount).isLessThan(ApplicationProperties().retry.maxAttempts)
        val totalElapsed = Duration.between(now, clock)
        assertThat(totalElapsed).isGreaterThan(Duration.ofHours(24))
        assertThat(totalElapsed).isLessThan(Duration.ofHours(27))
    }

    @Test
    fun `jitter stays within 20 percent bounds over many samples`() {
        val policy = policy()
        val expected = Duration.ofMinutes(1)

        repeat(200) {
            val result = policy.applyFailure(dto(), FailureKind.TRANSIENT, now)
            assertWithinJitter(delayOf(result), expected)
        }
    }

    @Test
    fun `transient failure schedules retry and fills bookkeeping fields`() {
        val result = policy().applyFailure(dto(tryingCount = 2, firstErrorAt = now.minusSeconds(60)), FailureKind.TRANSIENT, now)

        assertThat(result.state).isEqualTo(NotificationState.ERROR)
        assertThat(result.tryingCount).isEqualTo(3)
        assertThat(result.lastTryingDate).isEqualTo(now)
        assertThat(result.failureKind).isEqualTo(FailureKind.TRANSIENT)
        assertThat(result.nextRetryAt).isAfter(now)
    }

    @Test
    fun `permanent failure goes to FAILED without retry`() {
        val result = policy().applyFailure(dto(), FailureKind.PERMANENT, now)

        assertThat(result.state).isEqualTo(NotificationState.FAILED)
        assertThat(result.nextRetryAt).isNull()
        assertThat(result.failureKind).isEqualTo(FailureKind.PERMANENT)
        assertThat(result.tryingCount).isEqualTo(1)
    }

    @Test
    fun `attempts exhausted goes to EXPIRED`() {
        val policy = policy { maxAttempts = 3 }

        val result = policy.applyFailure(dto(tryingCount = 2, firstErrorAt = now.minusSeconds(60)), FailureKind.TRANSIENT, now)

        assertThat(result.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(result.nextRetryAt).isNull()
        // the increment counts the attempt that just failed, not the expiring transition itself
        assertThat(result.tryingCount).isEqualTo(3)
    }

    @Test
    fun `retry window exceeded goes to EXPIRED`() {
        val policy = policy { retryWindow = Duration.ofHours(24) }
        val firstErrorAt = now.minus(Duration.ofHours(25))

        val result = policy.applyFailure(dto(tryingCount = 5, firstErrorAt = firstErrorAt), FailureKind.TRANSIENT, now)

        assertThat(result.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(result.nextRetryAt).isNull()
    }

    @Test
    fun `failure exactly at window boundary is still retried`() {
        val policy = policy { retryWindow = Duration.ofHours(24) }
        val firstErrorAt = now.minus(Duration.ofHours(24))

        val result = policy.applyFailure(dto(tryingCount = 5, firstErrorAt = firstErrorAt), FailureKind.TRANSIENT, now)

        assertThat(result.state).isEqualTo(NotificationState.ERROR)
    }

    @Test
    fun `retries disabled sends transient failure straight to EXPIRED`() {
        val policy = policy { isEnabled = false }

        val result = policy.applyFailure(dto(), FailureKind.TRANSIENT, now)

        assertThat(result.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(result.nextRetryAt).isNull()
        assertThat(result.firstErrorAt).isEqualTo(now)
    }

    @Test
    fun `retries disabled sends permanent failure to FAILED not EXPIRED`() {
        val policy = policy { isEnabled = false }

        val result = policy.applyFailure(dto(), FailureKind.PERMANENT, now)

        assertThat(result.state).isEqualTo(NotificationState.FAILED)
        assertThat(result.nextRetryAt).isNull()
    }

    @Test
    fun `pre-send gate lets everything through while retries are enabled`() {
        val policy = policy()

        assertThat(policy.shouldAttempt(dto(tryingCount = 0))).isTrue()
        assertThat(policy.shouldAttempt(dto(tryingCount = 7))).isTrue()
    }

    @Test
    fun `pre-send gate stops already scheduled rows but not re-driven ones when retries are disabled`() {
        val policy = policy { isEnabled = false }

        // scheduled before the switch was flipped - must not be sent one more time
        assertThat(policy.shouldAttempt(dto(tryingCount = 1))).isFalse()
        // re-driven by hand: fresh budget, still gets its single attempt
        assertThat(policy.shouldAttempt(dto(tryingCount = 0))).isTrue()
    }

    @Test
    fun `expireWithoutAttempt closes the row without counting an attempt`() {
        val policy = policy { isEnabled = false }
        val scheduled = dto(tryingCount = 2, firstErrorAt = now.minus(Duration.ofMinutes(5)))
            .copy(nextRetryAt = now.plus(Duration.ofMinutes(15)))

        val result = policy.expireWithoutAttempt(scheduled)

        assertThat(result.state).isEqualTo(NotificationState.EXPIRED)
        assertThat(result.nextRetryAt).isNull()
        assertThat(result.tryingCount).isEqualTo(2)
        assertThat(result.firstErrorAt).isEqualTo(scheduled.firstErrorAt)
    }

    @Test
    fun `firstErrorAt is set on first failure and never overwritten`() {
        val policy = policy()

        val first = policy.applyFailure(dto(firstErrorAt = null), FailureKind.TRANSIENT, now)
        assertThat(first.firstErrorAt).isEqualTo(now)

        val later = now.plus(Duration.ofMinutes(10))
        val second = policy.applyFailure(first, FailureKind.TRANSIENT, later)
        assertThat(second.firstErrorAt).isEqualTo(now)
    }
}
