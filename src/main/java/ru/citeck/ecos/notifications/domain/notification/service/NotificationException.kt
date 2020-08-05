package ru.citeck.ecos.notifications.domain.notification.service

class NotificationException(msg: String) : RuntimeException(msg) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
