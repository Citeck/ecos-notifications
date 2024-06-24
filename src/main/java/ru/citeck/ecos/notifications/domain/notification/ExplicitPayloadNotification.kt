package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.webapp.api.entity.EntityRef

fun SendNotificationCommand.isExplicitMsgPayload(): Boolean {
    return templateRef == EntityRef.EMPTY
}

fun RawNotification.isExplicitMsgPayload(): Boolean {
    return template == null
}

fun RawNotification.isExplicitMsgTitle(): Boolean {
    return template == null || !title.isNullOrEmpty()
}
