package ru.citeck.ecos.notifications.domain.notification

enum class NotificationState {
    ERROR, SENT, EXPIRED, WAIT_FOR_DISPATCH, BLOCKED
}
