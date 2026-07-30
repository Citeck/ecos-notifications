package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.RawNotification

interface NotificationSenderService {
    /**
     * Attributes from all enabled sender dto's conditions
     */
    fun getModel(): Set<String>

    /**
     * @return result of the sender which processed the notification: its status and the meta
     * it reported (sign result, partial delivery note, ...)
     */
    fun sendNotification(notification: RawNotification): NotificationSenderResult
}
