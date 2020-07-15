package ru.citeck.ecos.notifications.service

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.Notification
import ru.citeck.ecos.notifications.domain.notification.NotificationType
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.service.providers.NotificationProvider
import java.util.*

@Service
class NotificationService(

    @Qualifier("notificationProviders")
    private val providers: Map<NotificationType, List<NotificationProvider>>,

    private val freemarkerService: FreemarkerTemplateEngineService

) {

    fun send(notification: Notification) {
        val title = prepareTitle(notification.template, notification.locale, notification.model)
        val body = prepareBody(notification.template, notification.locale, notification.model)

        val foundProviders = providers[notification.type]
            ?: throw NotificationException("Provider with notification type: ${notification.type} not registered}")
        foundProviders.forEach {
            it.send(title, body, notification.recipients, notification.from)
        }
    }

    private fun prepareBody(template: NotificationTemplateDto, locale: Locale, model: Map<String, Any>): String {
        val templateData = template.data[locale.toString()]
            ?: throw NotificationException("No template with locale <$locale> found for template: $template")

        return freemarkerService.process(templateData.name, String(templateData.data), model)
    }

    private fun prepareTitle(template: NotificationTemplateDto, locale: Locale, model: Map<String, Any>): String {
        val title = template.notificationTitle ?: return ""

        val titleTemplate = title.get(locale)
            ?: throw NotificationException("Notification title not found with locale: $locale in template: $template")

        return freemarkerService.process(template.id + "_title", titleTemplate, model)
    }
}
