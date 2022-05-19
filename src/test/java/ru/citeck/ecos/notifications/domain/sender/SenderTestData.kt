package ru.citeck.ecos.notifications.domain.sender

import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.lib.NotificationType

class SenderTestData {
    companion object {
        const val SOURCE_ID = "notifications-sender"
        const val NOTIFICATION_APP_ID = "notification"
        const val TEST_SENDER_ID = "test-sender-00"
        const val PROP_ID = "id"
        const val PROP_EXT_ID = "extId"
        const val PROP_NAME = "name"
        const val PROP_ORDER = "order"
        const val PROP_ENABLED = "enabled"

        @JvmStatic
        fun getTestSender(): NotificationsSenderDto {
            var sender = NotificationsSenderDto(TEST_SENDER_ID)
            sender.order = 0f
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

        @JvmStatic
        fun getEmptyId(): String {
            return NOTIFICATION_APP_ID + "/" + NotificationsSenderRecordsDao.ID + "@"
        }
    }
}
