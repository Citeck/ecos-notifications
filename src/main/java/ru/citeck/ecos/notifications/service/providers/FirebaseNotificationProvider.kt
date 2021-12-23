package ru.citeck.ecos.notifications.service.providers

import com.google.firebase.messaging.*
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType

const val DEVICE_TYPE_KEY = "device-type"
const val ACTION_ENTITY_ID = "action-entity-id"
const val MESSAGE_DATA_KEY = "message-data"

private const val DEVICE_ANDROID = "android"
private const val DEVICE_IOS = "ios"
private const val ERROR_CODE_TOKEN_NOT_REGISTERED = "registration-token-not-registered"

@Component
class FirebaseNotificationProvider : NotificationProvider {

    private val log = KotlinLogging.logger {}

    companion object {
        val resultOk = ProviderResult("ok")
        val resultError = ProviderResult("error")
        val resultActionDeletionRequires = ProviderResult("action-deletion-required")

    }

    override fun getType(): NotificationType {
        return NotificationType.FIREBASE_NOTIFICATION
    }

    override fun send(fitNotification: FitNotification): ProviderResult {
        log.debug("Send firebase message notification: $fitNotification")

        val registrationToken = resolveRegistrationToken(fitNotification)
        val fireBaseMessage: Message = buildMessage(registrationToken, fitNotification)

        val response: String
        try {
            log.debug {

                "Trying send message to firebase...\n" +
                    "title: ${fitNotification.title}\n" +
                    "body: ${fitNotification.body}\n" +
                    "messageData: ${getMessageData(fitNotification)}"

            }

            response = FirebaseMessaging.getInstance().send(fireBaseMessage)
        } catch (e: FirebaseMessagingException) {
            return if (ERROR_CODE_TOKEN_NOT_REGISTERED == e.errorCode) {
                log.info("Token <$registrationToken> is no longer registered")

                resultActionDeletionRequires
            } else {
                log.error("Failed to send firebase message", e)
                resultError
            }
        }

        log.debug("Successfully sent message: $response")
        return resultOk
    }

    private fun resolveRegistrationToken(fitNotification: FitNotification): String {
        if (fitNotification.recipients.isEmpty()) {
            throw FirebaseNotificationException("Cannot send firebase notification, recipient token is empty")
        }

        if (fitNotification.recipients.size > 1) {
            throw FirebaseNotificationException(
                "Only one recipient token is supported. " +
                    "Current size: ${fitNotification.recipients}. " +
                    "The message will be sent to the first in the list."
            )
        }

        return fitNotification.recipients.take(1)[0]
    }

    private fun buildMessage(registrationToken: String, fitNotification: FitNotification): Message {
        val messageData: Map<String, String> = getMessageData(fitNotification)

        val deviceType = getDeviceType(fitNotification)

        return when (deviceType) {
            DEVICE_ANDROID -> {
                buildAndroidMessage(
                    fitNotification.title ?: "",
                    fitNotification.body,
                    registrationToken,
                    messageData
                )
            }
            DEVICE_IOS -> {
                buildIosMessage(
                    fitNotification.title ?: "",
                    fitNotification.body,
                    registrationToken,
                    messageData
                )
            }
            else -> {
                throw IllegalStateException("Unsupported device type")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMessageData(fitNotification: FitNotification): Map<String, String> {
        return fitNotification.data[MESSAGE_DATA_KEY] as Map<String, String>
    }

    private fun getDeviceType(fitNotification: FitNotification): String {
        return fitNotification.data[DEVICE_TYPE_KEY] as String
    }

    private fun buildAndroidMessage(
        title: String,
        body: String,
        registrationToken: String,
        data: Map<String, String>
    ): Message {
        return Message.builder()
            .setNotification(
                Notification(
                    title,
                    body
                )
            )
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setTtl((3600 * 1000).toLong())
                    .setNotification(
                        AndroidNotification.builder()
                            .setIcon("stock_ticker_update")
                            .setColor("#f45342")
                            .build()
                    )
                    .build()
            )
            .setApnsConfig(
                ApnsConfig.builder()
                    .setAps(
                        Aps.builder()
                            .setBadge(42)
                            .build()
                    )
                    .build()
            )
            .putAllData(data)
            .setToken(registrationToken)
            .build()
    }

    private fun buildIosMessage(
        title: String,
        body: String,
        registrationToken: String,
        data: Map<String, String>
    ): Message {
        return Message.builder()
            .setNotification(
                Notification(
                    title,
                    body
                )
            )
            .setApnsConfig(
                ApnsConfig.builder()
                    .setAps(
                        Aps.builder()
                            .setBadge(42)
                            .build()
                    )
                    .build()
            )
            .putAllData(data)
            .setToken(registrationToken)
            .build()
    }

}

class FirebaseNotificationException(msg: String) : RuntimeException(msg)
