package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType

/**
 * @param T sender configuration
 */
interface NotificationSender<T> {
    fun sendNotification(notification: FitNotification, config: T): NotificationSenderResult

    fun getNotificationType(): NotificationType

    /**
     * Uses for matching with NotificationSenderEntity
     */
    fun getSenderType(): String

    fun getConfigClass(): Class<T>
}

data class NotificationSenderResult(
    val status: NotificationSenderSendStatus,
    val meta: Map<String, Any> = emptyMap()
) {

    companion object {
        /**
         * Meta key holding the partial-delivery note (String) of a send which was accepted
         * only for part of the recipients. Such a send is reported as SENT and is not retried.
         */
        const val PARTIAL_DELIVERY_NOTE = "partialDeliveryNote"
    }
}
