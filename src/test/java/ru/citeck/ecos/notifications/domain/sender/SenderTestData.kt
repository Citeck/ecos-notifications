package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.lib.NotificationType

class SenderTestData {
    companion object {
        const val SOURCE_ID = "notifications-sender"
        const val NOTIFICATION_APP_ID = "notification"
        const val TEST_SENDER_ID = "test-sender-00"
        const val PROP_ID = "id"
        const val PROP_NAME = "name"

        @JvmStatic
        fun getTestSender(): NotificationsSenderDto {
            var sender = NotificationsSenderDto(TEST_SENDER_ID)
            sender.order = 0.0f
            sender.notificationType = NotificationType.EMAIL_NOTIFICATION
            sender.name = "test notifications sender"
            return sender
        }

        @JvmStatic
        fun getNewSender(): NotificationsSenderDto {
            var sender = getTestSender()
            sender.id = null
            return sender
        }
    }
}
