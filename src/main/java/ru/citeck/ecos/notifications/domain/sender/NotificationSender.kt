package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType

/**
 * @param T sender configuration
 */
interface NotificationSender<T> {
    fun sendNotification(notification: FitNotification, config: T?): NotificationSenderSendStatus
    fun getNotificationType(): NotificationType

    /**
     * Uses for matching with NotificationSenderEntity
     */
    fun getSenderType(): String

    fun getConfigClass(): Class<T>
}
