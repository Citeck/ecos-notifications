package ru.citeck.ecos.notifications.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class RetryPropertiesBindingTest {

    @EnableConfigurationProperties(ApplicationProperties::class)
    class PropsConfig

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PropsConfig::class.java)

    @Test
    fun `retry block binds to typed fields`() {
        contextRunner
            .withPropertyValues(
                "ecos-notifications.retry.enabled=false",
                "ecos-notifications.retry.poll-interval=45s",
                "ecos-notifications.retry.batch-size=7",
                "ecos-notifications.retry.max-attempts=5",
                "ecos-notifications.retry.initial-interval=2m",
                "ecos-notifications.retry.multiplier=2.5",
                "ecos-notifications.retry.max-interval=1h",
                "ecos-notifications.retry.retry-window=12h",
                "ecos-notifications.retry.lease-time=10m"
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                val retry = context.getBean(ApplicationProperties::class.java).retry
                assertThat(retry.isEnabled).isFalse()
                assertThat(retry.pollInterval).isEqualTo(Duration.ofSeconds(45))
                assertThat(retry.batchSize).isEqualTo(7)
                assertThat(retry.maxAttempts).isEqualTo(5)
                assertThat(retry.initialInterval).isEqualTo(Duration.ofMinutes(2))
                assertThat(retry.multiplier).isEqualTo(2.5)
                assertThat(retry.maxInterval).isEqualTo(Duration.ofHours(1))
                assertThat(retry.retryWindow).isEqualTo(Duration.ofHours(12))
                assertThat(retry.leaseTime).isEqualTo(Duration.ofMinutes(10))
            }
    }

    @Test
    fun `retry defaults apply when block is absent`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            val retry = context.getBean(ApplicationProperties::class.java).retry
            assertThat(retry.isEnabled).isTrue()
            assertThat(retry.pollInterval).isEqualTo(Duration.ofSeconds(30))
            assertThat(retry.batchSize).isEqualTo(50)
            assertThat(retry.maxAttempts).isEqualTo(20)
            assertThat(retry.initialInterval).isEqualTo(Duration.ofMinutes(1))
            assertThat(retry.multiplier).isEqualTo(3.0)
            assertThat(retry.maxInterval).isEqualTo(Duration.ofHours(2))
            assertThat(retry.retryWindow).isEqualTo(Duration.ofHours(24))
            assertThat(retry.leaseTime).isEqualTo(Duration.ofMinutes(15))
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `deprecated error-notification properties do not fail startup`() {
        contextRunner
            .withPropertyValues(
                "ecos-notifications.error-notification.ttl=600000",
                "ecos-notifications.error-notification.delay=10000",
                "ecos-notifications.error-notification.min-try-count=3"
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                val props = context.getBean(ApplicationProperties::class.java)
                assertThat(props.errorNotification.minTryCount).isEqualTo(3)
            }
    }

    @Test
    fun `unknown property under prefix still fails startup`() {
        contextRunner
            .withPropertyValues("ecos-notifications.unknown-property=1")
            .run { context ->
                assertThat(context).hasFailed()
            }
    }
}
