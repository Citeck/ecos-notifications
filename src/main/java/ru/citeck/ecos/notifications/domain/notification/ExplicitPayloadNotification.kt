package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.records2.RecordRef

fun SendNotificationCommand.isExplicitMsgPayload(): Boolean {
    return templateRef == RecordRef.EMPTY
}

fun RawNotification.isExplicitMsgPayload(): Boolean {
    return template == null || !body.isNullOrEmpty()
}

fun RawNotification.isExplicitMsgTitle(): Boolean {
    return template == null || !title.isNullOrEmpty()
}
