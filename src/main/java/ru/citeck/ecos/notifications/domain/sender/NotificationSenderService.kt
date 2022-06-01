package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus

interface NotificationSenderService {
    /**
     * Attributes from all enabled sender dto's conditions
     */
    fun getModel(): Set<String>
    fun sendNotification(notification: RawNotification): NotificationSenderSendStatus
}
