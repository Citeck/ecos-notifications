package ru.citeck.ecos.notifications.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Duration
import javax.annotation.PostConstruct

/**
 * Startup sanity check of the `ecos-notifications.retry` block.
 *
 * The retry pipeline has no runtime guard against nonsensical values: `batch-size: 0` claims
 * nothing and silently stalls every retry, a non-positive `lease-time` makes a claim expire the
 * moment it is taken (so another replica may re-send rows that are still in flight), and a
 * `multiplier` below 1 turns the backoff into a tightening loop against an already failing SMTP
 * server. Such a stand looks healthy while quietly losing mail, so the context fails to start
 * instead.
 *
 * The documented lease invariant (`lease-time` > `batch-size` × worst-case send timeout) depends on
 * the SMTP timeouts, which live outside these properties — it can only be checked against the
 * effective environment, and it is reported as a WARN: the worst case rarely materializes and
 * refusing to start over it would be worse than a re-sent message.
 */
@Component
class RetryPropertiesValidator(
    private val props: ApplicationProperties,
    private val environment: Environment
) {

    companion object {
        private val log = KotlinLogging.logger {}

        /**
         * Per-message SMTP timeouts; the largest one is the worst case for a single send
         * (they bound different phases of the same dialogue, not one another).
         */
        val SMTP_TIMEOUT_PROPERTIES = listOf(
            "spring.mail.properties.mail.smtp.connectiontimeout",
            "spring.mail.properties.mail.smtp.timeout",
            "spring.mail.properties.mail.smtp.writetimeout"
        )

        fun findViolations(retry: ApplicationProperties.Retry): List<String> {
            val violations = mutableListOf<String>()

            fun checkPositive(name: String, value: Duration, consequence: String) {
                if (value.isZero || value.isNegative) {
                    violations.add("'$name' must be positive, but is $value ($consequence)")
                }
            }

            checkPositive("poll-interval", retry.pollInterval, "the retry job would never tick")
            checkPositive("initial-interval", retry.initialInterval, "the first retry would not be delayed")
            checkPositive("max-interval", retry.maxInterval, "the backoff cap would swallow every delay")
            checkPositive("retry-window", retry.retryWindow, "every failure would expire immediately")
            checkPositive("lease-time", retry.leaseTime, "a claim would expire before its rows are sent")

            if (retry.batchSize <= 0) {
                violations.add(
                    "'batch-size' must be positive, but is ${retry.batchSize} " +
                        "(a tick would claim nothing and the retry backlog would never drain)"
                )
            }
            if (retry.maxAttempts <= 0) {
                violations.add(
                    "'max-attempts' must be positive, but is ${retry.maxAttempts} " +
                        "(no notification would ever be attempted again)"
                )
            }
            if (retry.multiplier < 1.0) {
                violations.add(
                    "'multiplier' must be >= 1.0, but is ${retry.multiplier} " +
                        "(a shrinking backoff would hammer a failing server harder on every attempt)"
                )
            }
            return violations
        }
    }

    @PostConstruct
    fun validate() {
        val violations = findViolations(props.retry)
        check(violations.isEmpty()) {
            "Invalid 'ecos-notifications.retry' configuration: ${violations.joinToString("; ")}"
        }
        warnAboutLeaseInvariant()
    }

    private fun warnAboutLeaseInvariant() {
        // A non-numeric timeout must not take the context down: this check is WARN-only by
        // contract, and an unreadable value is exactly the "cannot be bounded" case below.
        val worstCaseSendMs = SMTP_TIMEOUT_PROPERTIES
            .mapNotNull { runCatching { environment.getProperty(it, Long::class.java) }.getOrNull() }
            .maxOrNull()

        if (worstCaseSendMs == null || worstCaseSendMs <= 0) {
            log.warn {
                "SMTP timeouts (${SMTP_TIMEOUT_PROPERTIES.joinToString(", ")}) are not set: an " +
                    "untimed socket can hold a claimed notification for as long as the SMTP server " +
                    "keeps it, so 'ecos-notifications.retry.lease-time' cannot bound a retry tick"
            }
            return
        }

        val worstCaseSend = Duration.ofMillis(worstCaseSendMs)
        val requiredLease = worstCaseSend.multipliedBy(props.retry.batchSize.toLong())
        if (props.retry.leaseTime <= requiredLease) {
            log.warn {
                "'ecos-notifications.retry.lease-time' (${props.retry.leaseTime}) does not exceed " +
                    "batch-size (${props.retry.batchSize}) x worst-case send timeout ($worstCaseSend) " +
                    "= $requiredLease: a claim lease may expire while its rows are still being sent, " +
                    "and another replica may re-send them"
            }
        }
    }
}
