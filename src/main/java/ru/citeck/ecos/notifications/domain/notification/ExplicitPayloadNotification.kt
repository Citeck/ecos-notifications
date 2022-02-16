package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand

fun SendNotificationCommand.isExplicitPayload(): Boolean {
    return body.isNotBlank()
}

fun RawNotification.isExplicitPayload(): Boolean {
    return body.isNotBlank()
}
