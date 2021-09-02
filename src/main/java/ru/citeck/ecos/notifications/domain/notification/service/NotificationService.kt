package ru.citeck.ecos.notifications.domain.notification.service

import com.sun.istack.internal.ByteArrayDataSource
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.NotificationConstants
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

    private val freemarkerService: FreemarkerTemplateEngineService,

    private val notificationEventService: NotificationEventService

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
            val fitNotification = FitNotification(
                title = title,
                body = body,
                recipients = rawNotification.recipients,
                from = rawNotification.from,
                cc = rawNotification.cc,
                bcc = rawNotification.bcc,
                attachments = attachments
            )

            it.send(fitNotification)

            notificationEventService.emitSendSuccess(
                NotificationEventDto(
                    rec = rawNotification.record,
                    notificationType = rawNotification.type,
                    notification = fitNotification,
                    model = rawNotification.model
                ),
                rawNotification.currentUser
            )

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

        val attachments = model[NotificationConstants.ATTACHMENTS] as? List<Map<String, Any>>
            ?: (model[NotificationConstants.ATTACHMENTS] as? Map<String, Any>)?.let { attach ->
                if (attach.isEmpty()) listOf()
                else listOf(attach)
            }
            ?: listOf()

        val result = mutableMapOf<String, DataSource>()

        attachments.forEach {
            val contentStr = it[NotificationConstants.BYTES] as? String
            if (contentStr.isNullOrBlank()) throw NotificationException("Attachment doesn't have content: $it")

            val fileBytes: ByteArray = Base64.getMimeDecoder().decode(contentStr)

            val fileInfoMap: Map<String, String> = it[NotificationConstants.PREVIEW_INFO] as Map<String, String>
            val fileName: String = getAttachmentName(fileInfoMap)
            val fileMimeType = fileInfoMap[NotificationConstants.MIMETYPE]
            if (fileMimeType.isNullOrBlank()) throw NotificationException("Attachment doesn't have mimetype: $it")

            result[fileName] = ByteArrayDataSource(fileBytes, fileMimeType)
        }

        return result
    }

    private fun getAttachmentName(infoAttachment: Map<String, String>): String {
        val fileName = infoAttachment[NotificationConstants.ORIGINAL_NAME]
        if (fileName.isNullOrBlank()) throw NotificationException("Attachment doesn't have name: $infoAttachment")
        val fileExt = infoAttachment[NotificationConstants.ORIGINAL_EXT]
        if (fileExt.isNullOrBlank()) throw NotificationException("Attachment doesn't have ext: $infoAttachment")

        return if (fileExt == fileName.takeLast(fileExt.length)) {
            fileName
        } else {
            fileName.plus(".").plus(fileExt)
        }
    }

    private fun resolveAnyAvailableTitle(titleMl: MLText, locale: Locale): String? {
        val result = MLText.getClosestValue(titleMl, locale)
        return result.ifBlank {
            null
        }
    }
}
