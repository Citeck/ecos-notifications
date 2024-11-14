package ru.citeck.ecos.notifications.domain.event.dto

import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.webapp.api.entity.EntityRef

data class NotificationEventDto(
    var rec: EntityRef = EntityRef.EMPTY,
    var notificationType: NotificationType,
    var notification: FitNotification,
    var model: Map<String, Any?>,

    var sendingMeta: Map<String, Any?>,
    var error: ErrorInfo? = null
)
