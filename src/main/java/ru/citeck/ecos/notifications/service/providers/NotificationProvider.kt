package ru.citeck.ecos.notifications.service.providers

import ru.citeck.ecos.notifications.lib.NotificationType

interface NotificationProvider {

    fun getType(): NotificationType

    fun send(title: String, body: String, to: List<String>, from: String)
}
