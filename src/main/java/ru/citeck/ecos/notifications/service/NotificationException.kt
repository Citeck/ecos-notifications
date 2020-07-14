package ru.citeck.ecos.notifications.service

import java.lang.RuntimeException

class NotificationException(msg: String) : RuntimeException(msg) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
