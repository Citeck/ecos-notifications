package ru.citeck.ecos.notifications.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

@Suppress("DEPRECATION")
class RetryPropertiesDeprecationWarnerTest {

    @Test
    fun `default values are not reported as overrides`() {
        val errorNotification = ApplicationProperties.ErrorNotification()

        val overrides = RetryPropertiesDeprecationWarner.findDeprecatedOverrides(errorNotification)

        assertThat(overrides).isEmpty()
    }

    @Test
    fun `overridden deprecated values are reported`() {
        val errorNotification = ApplicationProperties.ErrorNotification()
        errorNotification.ttl = 30000
        errorNotification.minTryCount = 1

        val overrides = RetryPropertiesDeprecationWarner.findDeprecatedOverrides(errorNotification)

        assertThat(overrides).containsExactly("ttl", "min-try-count")
    }

    @Test
    fun `all overridden values are reported`() {
        val errorNotification = ApplicationProperties.ErrorNotification()
        errorNotification.ttl = -1
        errorNotification.delay = 5000
        errorNotification.minTryCount = 99

        val overrides = RetryPropertiesDeprecationWarner.findDeprecatedOverrides(errorNotification)

        assertThat(overrides).containsExactly("ttl", "delay", "min-try-count")
    }

    @Test
    fun `startup logs a WARN when deprecated properties are set`() {
        val props = ApplicationProperties()
        props.errorNotification.ttl = 600000
        props.errorNotification.minTryCount = 1

        val events = captureWarnerLog { RetryPropertiesDeprecationWarner(props).warnAboutDeprecatedOverrides() }

        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.WARN)
        assertThat(events[0].formattedMessage)
            .contains("'ecos-notifications.error-notification.*' is deprecated and ignored")
            .contains("use 'ecos-notifications.retry.*'")
            .contains("ttl", "min-try-count")
    }

    @Test
    fun `startup stays silent when deprecated properties are untouched`() {
        val events = captureWarnerLog {
            RetryPropertiesDeprecationWarner(ApplicationProperties()).warnAboutDeprecatedOverrides()
        }

        assertThat(events).isEmpty()
    }

    private fun captureWarnerLog(action: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(RetryPropertiesDeprecationWarner::class.java) as Logger
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
