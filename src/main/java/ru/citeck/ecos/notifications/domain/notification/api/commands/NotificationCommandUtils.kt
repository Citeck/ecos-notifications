package ru.citeck.ecos.notifications.domain.notification.api.commands

import ru.citeck.ecos.webapp.api.entity.EntityRef

class NotificationCommandUtils {

    companion object {
        fun resolveNotificationRecord(record: Any?): EntityRef {
            if (record is EntityRef) {
                return record
            }

            if (record is String) {
                return EntityRef.valueOf(record)
            }

            return EntityRef.EMPTY
        }
    }
}
