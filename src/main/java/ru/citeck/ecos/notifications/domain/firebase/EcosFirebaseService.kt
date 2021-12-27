package ru.citeck.ecos.notifications.domain.firebase

import com.google.firebase.messaging.*
import mu.KotlinLogging
import org.springframework.stereotype.Service

private const val ERROR_CODE_TOKEN_NOT_REGISTERED = "registration-token-not-registered"

@Service
class EcosFirebaseService {

    private val log = KotlinLogging.logger {}

    fun sendMessage(firebaseMessage: FirebaseMessage): FirebaseMessageResult {
        log.debug("Send firebase message: $firebaseMessage")

        val response: String

        try {
            val fireBaseMessage: Message = buildMessage(firebaseMessage)

            response = FirebaseMessaging.getInstance().send(fireBaseMessage)
        } catch (e: Exception) {
            return when (e) {
                is FirebaseMessagingException -> {
                    if (ERROR_CODE_TOKEN_NOT_REGISTERED == e.errorCode) {
                        log.info("Token <${firebaseMessage.token}> is no longer registered")
                        FirebaseMessageResult.TOKEN_NOT_REGISTERED
                    } else {
                        log.error("Failed to send firebase message", e)
                        FirebaseMessageResult.ERROR
                    }
                }
                else -> {
                    log.error("Failed to send firebase message", e)
                    FirebaseMessageResult.ERROR
                }
            }
        }

        log.debug { "firebase response: $response" }

        return FirebaseMessageResult.OK
    }

    private fun buildMessage(firebaseMessage: FirebaseMessage): Message {
        return when (firebaseMessage.deviceType) {
            DeviceType.ANDROID -> {
                buildAndroidMessage(firebaseMessage)
            }
            DeviceType.IOS -> {
                buildIosMessage(firebaseMessage)
            }
        }
    }

    private fun buildAndroidMessage(firebaseMessage: FirebaseMessage): Message {
        return Message.builder()
            .setNotification(
                Notification(
                    firebaseMessage.title,
                    firebaseMessage.body
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
            .putAllData(firebaseMessage.messageData)
            .setToken(firebaseMessage.token)
            .build()
    }

    private fun buildIosMessage(firebaseMessage: FirebaseMessage): Message {
        return Message.builder()
            .setNotification(
                Notification(
                    firebaseMessage.title,
                    firebaseMessage.body
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
            .putAllData(firebaseMessage.messageData)
            .setToken(firebaseMessage.token)
            .build()
    }

}

data class FirebaseMessage(
    val token: String,
    val title: String,
    val body: String,
    val deviceType: DeviceType,
    val messageData: Map<String, String>
)

enum class DeviceType(val value: String) {
    ANDROID("android"), IOS("ios");

    companion object {
        fun from(value: String): DeviceType = values().find { it.value == value }
            ?: throw IllegalArgumentException("Device type for value: $value not found")
    }
}

enum class FirebaseMessageResult {
    OK, ERROR, TOKEN_NOT_REGISTERED
}
