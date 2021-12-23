package ru.citeck.ecos.notifications.service.providers

import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType

interface NotificationProvider {

    fun getType(): NotificationType

    fun send(fitNotification: FitNotification): ProviderResult
}

data class ProviderResult(val code: String)
