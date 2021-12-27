package ru.citeck.ecos.notifications.service.providers

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.firebase.*
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
import ru.citeck.ecos.notifications.lib.NotificationType

const val DEVICE_TYPE_KEY = "device-type"
const val ACTION_ENTITY_ID = "action-entity-id"
const val MESSAGE_DATA_KEY = "message-data"


@Component
class FirebaseNotificationProvider(
    private val ecosFirebaseService: EcosFirebaseService,
    private val actionService: ActionService
) : NotificationProvider {

    private val log = KotlinLogging.logger {}

    override fun getType(): NotificationType {
        return NotificationType.FIREBASE_NOTIFICATION
    }

    override fun send(fitNotification: FitNotification) {
        log.debug("Send firebase message notification: $fitNotification")

        val registrationToken = resolveRegistrationToken(fitNotification)

        val fireBaseMessage = FirebaseMessage(
            title = fitNotification.title ?: "",
            body = fitNotification.body,
            token = registrationToken,
            deviceType = resolveDeviceType(fitNotification),
            messageData = resolveMessageData(fitNotification)
        )

        val response = ecosFirebaseService.sendMessage(fireBaseMessage)

        if (response == FirebaseMessageResult.TOKEN_NOT_REGISTERED) {
            deleteSubscriptionAction(fitNotification)
        }
    }

    private fun resolveRegistrationToken(fitNotification: FitNotification): String {
        if (fitNotification.recipients.isEmpty()) {
            throw EcosFirebaseNotificationException("Cannot send firebase notification, recipient token is empty")
        }

        if (fitNotification.recipients.size > 1) {
            throw EcosFirebaseNotificationException(
                "Only one recipient token is supported. " +
                    "Current size: ${fitNotification.recipients}. " +
                    "The message will be sent to the first in the list."
            )
        }

        return fitNotification.recipients.take(1)[0]
    }

    private fun resolveDeviceType(fitNotification: FitNotification): DeviceType {
        val device = fitNotification.data[DEVICE_TYPE_KEY] as String
        return DeviceType.valueOf(device)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveMessageData(fitNotification: FitNotification): Map<String, String> {
        return fitNotification.data[MESSAGE_DATA_KEY] as Map<String, String>
    }

    private fun deleteSubscriptionAction(fitNotification: FitNotification) {
        val actionId: Long

        try {
            actionId = fitNotification.data[ACTION_ENTITY_ID] as Long
            actionService.deleteById(actionId)
        } catch (e: Exception) {
            log.error("Failed to delete subscription action by id", e)
        }
    }

}
