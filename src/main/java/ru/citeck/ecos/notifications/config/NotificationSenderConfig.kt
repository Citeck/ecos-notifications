package ru.citeck.ecos.notifications.config

import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.notifications.domain.sender.NotificationSender

@Slf4j
@Configuration
class NotificationSenderConfig {

    companion object {
        val log = KotlinLogging.logger {}
    }

    @Bean(name = ["notificationSenders"])
    fun notificationSenders(context: ApplicationContext): Map<String, List<NotificationSender<Any>>> {
        val sendersMap = mutableMapOf<String, MutableList<NotificationSender<Any>>>()

        context.getBeansOfType(NotificationSender::class.java).forEach { (id, sender) ->
            sendersMap.computeIfAbsent(sender.getSenderType()) { mutableListOf() }
                .add(sender as NotificationSender<Any>)
            log.info(
                "Register notification sender with id: $id, type: ${sender.getSenderType()}," +
                    "notification type: ${sender.getNotificationType()}")
        }

        return sendersMap
    }

}
