package ru.citeck.ecos.notifications.domain.event.dto

import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef

data class NotificationEventDto(
    var rec: RecordRef = RecordRef.EMPTY,
    var notificationType: NotificationType,
    var notification: FitNotification,
    var model: Map<String, Any>,

    var error: ErrorInfo? = null
)
