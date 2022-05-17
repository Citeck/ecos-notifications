package ru.citeck.ecos.notifications.domain.sender

enum class NotificationSenderSendStatus {
    /**
     * Notification was sent. No further processing required
     */
    SENT,

    /**
     * Notification sending is blocked. No further processing required
     */
    BLOCKED,

    /**
     * Notification was not processed by the current Sender. Have to use the next one.
     */
    SKIPPED
}
