package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.FitNotification

interface NotificationSenderService {

    fun getModel(): Set<String>
    fun sendNotification(notification: FitNotification): NotificationSenderSendStatus
}
