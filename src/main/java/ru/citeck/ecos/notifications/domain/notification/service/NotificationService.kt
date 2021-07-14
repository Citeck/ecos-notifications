package ru.citeck.ecos.notifications.domain.notification.service

import com.sun.istack.internal.ByteArrayDataSource
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.providers.NotificationProvider
import java.util.*
import javax.activation.DataSource

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
        val attachments = prepareAttachments(rawNotification.model)

        val foundProviders = providers[rawNotification.type]
            ?: throw NotificationException("Provider with notification type: ${rawNotification.type} not registered}")
        foundProviders.forEach {
            it.send(FitNotification(
                title = title,
                body = body,
                recipients = rawNotification.recipients,
                from = rawNotification.from,
                cc = rawNotification.cc,
                bcc = rawNotification.bcc,
                attachments = attachments
            ))
        }
    }

    private fun prepareBody(template: NotificationTemplateWithMeta, locale: Locale, model: Map<String, Any>): String {
        return freemarkerService.process(template.id, locale, model)
    }

    private fun prepareTitle(template: NotificationTemplateWithMeta, locale: Locale, model: Map<String, Any>): String {
        val title = template.notificationTitle ?: return ""

        val titleTemplate = resolveAnyAvailableTitle(title, locale)
            ?: throw NotificationException("Notification title not found in template: $template")

        return freemarkerService.process(template.id + "_title", titleTemplate, model)
    }

    private fun prepareAttachments(model: Map<String, Any>): Map<String, DataSource> {

        val attachments = model["_attachments"] as? List<Map<String, Any>>
            ?: (model["_attachments"] as? Map<String, Any>)?.let { listOf(it) }
            ?: listOf()

        val result = mutableMapOf<String, DataSource>()

        attachments.forEach {
            val contentStr: String = it["bytes"] as String?
                ?: throw NotificationException("Attachment don't have content: $it")

            val fileBytes: ByteArray = Base64.getMimeDecoder().decode(contentStr)

            val fileInfoMap: Map<String, String> = it["previewInfo"] as Map<String, String>
            val fileName: String = getAttachmentName(fileInfoMap)
            val fileMimeType: String = fileInfoMap["mimetype"]
                ?: throw NotificationException("Attachment don't have mimetype: $it")

            result[fileName] = ByteArrayDataSource(fileBytes, fileMimeType)
        }

        return result
    }

    private fun getAttachmentName(infoAttachment: Map<String, String>): String {
        val fileName: String = infoAttachment["originalName"]
            ?: throw NotificationException("Attachment don't have name: $infoAttachment")
        val fileExt: String = infoAttachment["originalExt"]
            ?: throw NotificationException("Attachment don't have ext: $infoAttachment")

        return if (fileExt.equals(fileName.takeLast(fileExt.length))) {
            fileName
        } else {
            fileName.plus(".").plus(fileExt)
        }
    }

    private fun resolveAnyAvailableTitle(titleMl: MLText, locale: Locale): String? {
        val result = MLText.getClosestValue(titleMl, locale)
        return if (result.isBlank()) {
            null
        } else {
            result
        }
    }
}
