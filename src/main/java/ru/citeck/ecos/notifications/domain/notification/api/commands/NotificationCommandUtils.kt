package ru.citeck.ecos.notifications.domain.notification.api.commands

import ru.citeck.ecos.records2.RecordRef

class NotificationCommandUtils {

    companion object {
        fun resolveNotificationRecord(record: Any?): RecordRef {
            if (record is RecordRef) {
                return record
            }

            if (record is String) {
                return RecordRef.valueOf(record)
            }

            return RecordRef.EMPTY
        }
    }
}
