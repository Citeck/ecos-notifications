package ru.citeck.ecos.notifications.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.env.MockEnvironment
import java.time.Duration

class RetryPropertiesValidatorTest {

    @Test
    fun `default configuration is valid`() {
        assertThat(RetryPropertiesValidator.findViolations(ApplicationProperties.Retry())).isEmpty()
    }

    @Test
    fun `zero batch size is rejected`() {
        val retry = ApplicationProperties.Retry()
        retry.batchSize = 0

        assertThat(RetryPropertiesValidator.findViolations(retry))
            .singleElement().asString().contains("'batch-size' must be positive")
    }

    @Test
    fun `non-positive durations and counts are rejected`() {
        val retry = ApplicationProperties.Retry()
        retry.pollInterval = Duration.ZERO
        retry.initialInterval = Duration.ofSeconds(-1)
        retry.maxInterval = Duration.ZERO
        retry.retryWindow = Duration.ZERO
        retry.leaseTime = Duration.ofMinutes(-5)
        retry.maxAttempts = 0
        retry.multiplier = 0.5

        val violations = RetryPropertiesValidator.findViolations(retry)

        assertThat(violations).hasSize(7)
        assertThat(violations.joinToString())
            .contains("'poll-interval'")
            .contains("'initial-interval'")
            .contains("'max-interval'")
            .contains("'retry-window'")
            .contains("'lease-time'")
            .contains("'max-attempts'")
            .contains("'multiplier'")
    }

    @Test
    fun `startup fails on invalid configuration`() {
        val props = ApplicationProperties()
        props.retry.batchSize = 0

        assertThatThrownBy { RetryPropertiesValidator(props, smtpEnvironment(10_000)).validate() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Invalid 'ecos-notifications.retry' configuration")
            .hasMessageContaining("'batch-size' must be positive")
    }

    @Test
    fun `startup passes silently when the lease invariant holds`() {
        val props = ApplicationProperties()
        props.retry.batchSize = 50
        props.retry.leaseTime = Duration.ofMinutes(15)

        val events = captureValidatorLog {
            assertThatCode { RetryPropertiesValidator(props, smtpEnvironment(10_000)).validate() }
                .doesNotThrowAnyException()
        }

        assertThat(events).isEmpty()
    }

    @Test
    fun `too short lease is reported as a warning, not a failure`() {
        val props = ApplicationProperties()
        props.retry.batchSize = 50
        props.retry.leaseTime = Duration.ofMinutes(1)

        val events = captureValidatorLog {
            assertThatCode { RetryPropertiesValidator(props, smtpEnvironment(10_000)).validate() }
                .doesNotThrowAnyException()
        }

        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.WARN)
        assertThat(events[0].formattedMessage)
            .contains("'ecos-notifications.retry.lease-time'")
            .contains("does not exceed")
    }

    @Test
    fun `missing smtp timeouts are reported as a warning`() {
        val events = captureValidatorLog {
            RetryPropertiesValidator(ApplicationProperties(), MockEnvironment()).validate()
        }

        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.WARN)
        assertThat(events[0].formattedMessage).contains("SMTP timeouts")
    }

    private fun smtpEnvironment(timeoutMs: Long): MockEnvironment {
        val environment = MockEnvironment()
        RetryPropertiesValidator.SMTP_TIMEOUT_PROPERTIES.forEach {
            environment.setProperty(it, timeoutMs.toString())
        }
        return environment
    }

    private fun captureValidatorLog(action: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(RetryPropertiesValidator::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list
    }
}
