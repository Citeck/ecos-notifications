package ru.citeck.ecos.notifications.listener

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationsProperties
import ru.citeck.ecos.notifications.service.senders.EmailNotificationSender

@Profile("!test")
@Component
class ApplicationStartupListener(
    private val notificationProvider: EmailNotificationSender,
    private val notificationsProperties: NotificationsProperties,
    private val applicationProperties: ApplicationProperties
) {

    @EventListener(ApplicationReadyEvent::class)
    fun onNotificationsAppReady() {
        if (applicationProperties.startupNotification.isEnabled) {
            val startupNotification = FitNotification(
                applicationProperties.startupNotification.body,
                applicationProperties.startupNotification.title,
                setOf(applicationProperties.startupNotification.recipient),
                notificationsProperties.defaultFrom
            )
            notificationProvider.sendNotification(startupNotification)
        }
    }
}
