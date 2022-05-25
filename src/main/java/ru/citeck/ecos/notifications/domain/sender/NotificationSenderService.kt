package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.RawNotification

interface NotificationSenderService {

    fun getModel(): Set<String>
    fun sendNotification(notification: RawNotification): NotificationSenderSendStatus
}
