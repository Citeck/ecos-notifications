package ru.citeck.ecos.notifications.domain.notification.service

open class NotificationException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
