package ru.citeck.ecos.notifications.config

import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.providers.NotificationProvider

@Slf4j
@Deprecated("NotificationProvider replaced by NotificationSender")
@Configuration
class NotificationProvidersConfig {

    companion object {
        val log = KotlinLogging.logger {}
    }

    @Bean(name = ["notificationProviders"])
    fun notificationProviders(context: ApplicationContext): Map<NotificationType, List<NotificationProvider>> {
        val providers = mutableMapOf<NotificationType, MutableList<NotificationProvider>>()

        context.getBeansOfType(NotificationProvider::class.java).forEach { (id, provider) ->
            providers.computeIfAbsent(provider.getType()) { mutableListOf() }.add(provider)
            log.info("Register notification provider with id: $id")
        }

        return providers
    }

}
