package ru.citeck.ecos.notifications.domain.notification

enum class NotificationResultStatus(val value: String) {
    OK("ok"),
    ERROR("error"),
    RECIPIENTS_NOT_FOUND("recipients-not-found")
}
