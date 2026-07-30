package ru.citeck.ecos.notifications.domain.notification

/**
 * Classification of a notification send failure.
 *
 * [TRANSIENT] — the failure may resolve by itself (SMTP outage, timeout, 4xx reply),
 * the notification is eligible for retry.
 * [PERMANENT] — retrying is pointless (invalid address, broken template),
 * the notification goes to [NotificationState.FAILED] immediately.
 */
enum class FailureKind {
    TRANSIENT,
    PERMANENT
}
