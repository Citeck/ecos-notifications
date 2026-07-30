package ru.citeck.ecos.notifications.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/**
 * Logs a WARN at startup when deprecated `ecos-notifications.error-notification.*` properties
 * are overridden. The values are accepted (removing them from [ApplicationProperties] would
 * crash stands that still set them because of `ignoreUnknownFields = false`) but ignored —
 * the retry mechanism is configured via `ecos-notifications.retry.*`.
 */
@Component
class RetryPropertiesDeprecationWarner(
    private val props: ApplicationProperties
) {

    companion object {
        private val log = KotlinLogging.logger {}

        @Suppress("DEPRECATION")
        fun findDeprecatedOverrides(errorNotification: ApplicationProperties.ErrorNotification): List<String> {
            val overrides = mutableListOf<String>()
            if (errorNotification.ttl != NotificationsDefault.ErrorNotification.TTL) {
                overrides.add("ttl")
            }
            if (errorNotification.delay != NotificationsDefault.ErrorNotification.DELAY) {
                overrides.add("delay")
            }
            if (errorNotification.minTryCount != NotificationsDefault.ErrorNotification.MIN_TRY_COUNT) {
                overrides.add("min-try-count")
            }
            return overrides
        }
    }

    @PostConstruct
    @Suppress("DEPRECATION")
    fun warnAboutDeprecatedOverrides() {
        val overrides = findDeprecatedOverrides(props.errorNotification)
        if (overrides.isNotEmpty()) {
            log.warn {
                "'ecos-notifications.error-notification.*' is deprecated and ignored, " +
                    "use 'ecos-notifications.retry.*'. Overridden deprecated properties: $overrides"
            }
        }
    }
}
