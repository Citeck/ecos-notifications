package ru.citeck.ecos.notifications.service.providers

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.firebase.*
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType

@Component
class FirebaseNotificationProvider(
    private val ecosFirebaseService: EcosFirebaseService,
    private val actionService: ActionService
) : NotificationProvider, NotificationSender<Unit> {

    private val log = KotlinLogging.logger {}

    override fun getConfigClass(): Class<Unit> {
        return Unit::class.java
    }

    override fun getSenderType(): String {
        return "default"
    }

    override fun getNotificationType(): NotificationType {
        return NotificationType.FIREBASE_NOTIFICATION
    }

    override fun sendNotification(notification: FitNotification, config: Unit): NotificationSenderSendStatus {
        send(notification)
        return NotificationSenderSendStatus.SENT
    }

    override fun getType(): NotificationType {
        return NotificationType.FIREBASE_NOTIFICATION
    }

    override fun send(fitNotification: FitNotification) {
        log.debug { "Send firebase message notification: $fitNotification" }

        val registrationToken = resolveRegistrationToken(fitNotification)

        val fireBaseMessage = FirebaseMessage(
            title = fitNotification.title ?: "",
            body = fitNotification.body,
            token = registrationToken,
            deviceType = resolveDeviceType(fitNotification),
            messageData = resolveMessageData(fitNotification)
        )

        val response = ecosFirebaseService.sendMessage(fireBaseMessage)

        when (response.resultCode) {
            FirebaseMessageResultCode.TOKEN_NOT_REGISTERED -> {
                deleteSubscriptionAction(fitNotification)
                throw EcosFirebaseNotificationException(
                    response.message + ". firebaseCode: ${response.firebaseErrorCode}"
                )
            }
            FirebaseMessageResultCode.ERROR -> {
                throw EcosFirebaseNotificationException(
                    response.message + ". firebaseCode: ${response.firebaseErrorCode}"
                )
            }
            else -> log.debug("Success sending firebase message: ${response.message}")
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
        val device = fitNotification.data[FIREBASE_CONFIG_DEVICE_TYPE_KEY] as String
        return DeviceType.from(device)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveMessageData(fitNotification: FitNotification): Map<String, String> {
        if (!fitNotification.data.containsKey(FIREBASE_MESSAGE_DATA_KEY)) {
            return emptyMap()
        }

        return fitNotification.data[FIREBASE_MESSAGE_DATA_KEY] as Map<String, String>
    }

    private fun deleteSubscriptionAction(fitNotification: FitNotification) {
        val actionId: Long

        try {
            if (fitNotification.data.containsKey(FIREBASE_ACTION_ENTITY_ID_KEY)) {
                actionId = fitNotification.data[FIREBASE_ACTION_ENTITY_ID_KEY] as Long

                actionService.findById(actionId).ifPresent {
                    actionService.deleteById(it.id)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to delete subscription action by id. Data:\n" + fitNotification.data }
        }
    }
}
