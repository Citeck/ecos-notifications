package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType

interface NotificationSender<T> {
    fun sendNotification(notification: FitNotification, config: T): NotificationSenderSendStatus
    fun getNotificationType(): NotificationType
    fun getSenderType(): String
}
