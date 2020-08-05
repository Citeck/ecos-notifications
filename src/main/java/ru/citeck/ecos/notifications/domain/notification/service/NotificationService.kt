package ru.citeck.ecos.notifications.domain.notification.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.FreemarkerTemplateEngineService
import ru.citeck.ecos.notifications.service.providers.NotificationProvider
import java.util.*

@Service
class NotificationService(

    @Qualifier("notificationProviders")
    private val providers: Map<NotificationType, List<NotificationProvider>>,

    private val freemarkerService: FreemarkerTemplateEngineService

) {

    private val log = KotlinLogging.logger {}

    fun send(rawNotification: RawNotification) {
        log.debug("Send notification raw: $rawNotification")

        val title = prepareTitle(rawNotification.template, rawNotification.locale, rawNotification.model)
        val body = prepareBody(rawNotification.template, rawNotification.locale, rawNotification.model)

        val foundProviders = providers[rawNotification.type]
            ?: throw NotificationException("Provider with notification type: ${rawNotification.type} not registered}")
        foundProviders.forEach {
            it.send(FitNotification(
                title = title,
                body = body,
                recipients = rawNotification.recipients,
                from = rawNotification.from,
                cc = rawNotification.cc,
                bcc = rawNotification.bcc
            ))
        }
    }

    private fun prepareBody(template: NotificationTemplateWithMeta, locale: Locale, model: Map<String, Any>): String {
        val templateData = template.templateData[locale.toString()]
            ?: throw NotificationException("No template with locale <$locale> found for template: $template")

        return freemarkerService.process(templateData.name, String(templateData.data), model)
    }

    private fun prepareTitle(template: NotificationTemplateWithMeta, locale: Locale, model: Map<String, Any>): String {
        val title = template.notificationTitle ?: return ""

        val titleTemplate = title.get(locale)
            ?: throw NotificationException("Notification title not found with locale: $locale in template: $template")

        return freemarkerService.process(template.id + "_title", titleTemplate, model)
    }
}
