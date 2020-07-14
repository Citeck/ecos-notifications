package ru.citeck.ecos.notifications.service.providers

import ru.citeck.ecos.notifications.domain.notification.NotificationType

interface NotificationProvider {

    fun getType(): NotificationType

    fun send(title: String, body: String, to: List<String>)
}
