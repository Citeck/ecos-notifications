package ru.citeck.ecos.notifications.domain.notification.service

/**
 * Signals that a notification send failure is permanent: retrying cannot succeed
 * (e.g. template not found, broken template markup, invalid sender configuration).
 *
 * [NotificationFailureClassifier] treats any exception chain containing this type
 * as [ru.citeck.ecos.notifications.domain.notification.FailureKind.PERMANENT].
 */
class NotificationPermanentException(msg: String, cause: Throwable? = null) : NotificationException(msg, cause) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
