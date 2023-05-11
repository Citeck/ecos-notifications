package ru.citeck.ecos.notifications.domain.notification.service

import com.sun.istack.internal.ByteArrayDataSource
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.server.MimeMappings
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus.*
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.element.elematts.RecordAttsElement
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import java.util.*
import javax.activation.DataSource
import org.springframework.mail.javamail.MimeMessageHelper
import ru.citeck.ecos.notifications.domain.notification.*
import ru.citeck.ecos.records2.RecordRef

@Component
class NotificationSenderServiceImpl(

    @Qualifier("notificationSenders")
    private val sendersMap: Map<String, List<NotificationSender<Any>>>,

    private val freemarkerService: FreemarkerTemplateEngineService,

    private val notificationEventService: NotificationEventService,
    private val notificationsSenderService: NotificationsSenderService,
    private val predicateService: PredicateService

) : NotificationSenderService {

    private val log = KotlinLogging.logger {}

    override fun getModel(): Set<String> {
        return notificationsSenderService.getAllEnabled()
            .asSequence()
            .filter { it.condition != null }
            .map { PredicateUtils.getAllPredicateAttributes(it.condition) }
            .filter { it.isNotEmpty() }
            .flatten()
            .toSet()
    }

    override fun sendNotification(notification: RawNotification): NotificationSenderSendStatus {
        log.debug("Send notification raw $notification")

        val senders = notificationsSenderService.getEnabled(
            Predicates.eq(NotificationsSenderEntity.PROP_NOTIFICATION_TYPE, notification.type), null
        )
        if (senders.isEmpty()) {
            throw NotificationException("Failed to find notifications sender for type '${notification.type}'")
        }
        val fitNotification = convertRawNotificationToFit(notification)

        senders.forEach { sender ->
            if (sender.senderType == null) {
                log.warn { "Sender type is undefined at '${sender.id}' notifications sender" }
                return@forEach
            }

            if (sender.templates.isNotEmpty() && notification.template != null) {
                var acceptable = false
                for (recordRef in sender.templates) {
                    if (recordRef.id == notification.template.id) {
                        acceptable = true
                        break
                    }
                }
                if (!acceptable) {
                    log.debug { "Sender '${sender.id}' does not fit for notification by template" }
                    return@forEach
                }
            }

            if (sender.condition != null) {
                if (notification.model.isEmpty()) {
                    log.debug {
                        "Sender '${sender.id}' does not fit for notification " +
                            "with empty model by condition '${sender.condition}'"
                    }
                    return@forEach
                }

                val attributes = ObjectData.create()
                notification.model.forEach { (attName, value) ->
                    attributes.set(attName, value)
                }

                val recAtts = RecordAtts()
                recAtts.setAtts(attributes)
                val element = RecordAttsElement("", recAtts)
                val acceptable = predicateService.isMatch(element, sender.condition!!)
                if (!acceptable) {
                    log.debug { "Sender '${sender.id}' does not fit for notification by condition '${sender.condition}'" }
                    return@forEach
                }
            }

            val senderBeanList = sendersMap[sender.senderType]
                ?: throw NotificationException(
                    "Failed to find sender implementation for type '${sender.senderType}' " +
                        "for '${sender.id}' notifications sender"
                )
            val notificationEventDto = NotificationEventDto(
                rec = notification.record,
                notificationType = notification.type,
                notification = fitNotification,
                model = notification.model
            )

            for (senderBean in senderBeanList) {
                if (senderBean.getNotificationType() != sender.notificationType) {
                    continue
                }
                log.debug { "Send notification through sender '${sender.id}' with type '${sender.senderType}'" }

                val configClass = senderBean.getConfigClass()
                val config = sender.senderConfig.getAs(configClass) ?: error(
                    "Failed to get sender config. " +
                        "Config: ${sender.senderConfig} as class $configClass"
                )

                val result = senderBean.sendNotification(fitNotification, config)

                when (result) {
                    SENT -> notificationEventService.emitSendSuccess(notificationEventDto)
                    BLOCKED -> notificationEventService.emitSendBlocked(notificationEventDto)
                    SKIPPED -> continue
                }
                return result
            }
        }

        throw NotificationException("Failed to send notification. None of the senders returned a result")
    }

    private fun convertRawNotificationToFit(rawNotification: RawNotification): FitNotification {
        val title = if (rawNotification.isExplicitMsgTitle()) {
            rawNotification.title
        } else {
            prepareTitle(rawNotification.template!!, rawNotification.locale, rawNotification.model)
        }
        //TODO: Revert this.
        // 99.9% that this is not a fix for the problem and will be reproduced in the future.
        // Decided to merge and watch.
        var body: String? = null
        if (rawNotification.isExplicitMsgPayload()) {
            body = rawNotification.body
        } else {
            body = prepareBody(rawNotification.template!!, rawNotification.locale, rawNotification.model)
        }
        val attachments = prepareAttachments(rawNotification.model)
        val data = prepareData(rawNotification.model)
        return FitNotification(
            title = title,
            body = body,
            recipients = rawNotification.recipients,
            from = rawNotification.from,
            cc = rawNotification.cc,
            bcc = rawNotification.bcc,
            attachments = attachments,
            data = data,
            templateRef = rawNotification.template?.let {
                RecordRef.Companion.valueOf("notifications/template@" + rawNotification.template.id)
            }
        )
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

    @Suppress("UNCHECKED_CAST")
    private fun prepareData(model: Map<String, Any>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result[NotificationConstants.MODEL] = model.toMap()
        if (model[NotificationConstants.DATA] == null) {
            return result
        }
        result.putAll(model[NotificationConstants.DATA] as Map<String, Any>)
        return result
    }

    @Suppress("UNCHECKED_CAST")
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
            log.trace { "Attachment preview info:\n $fileInfoMap" }
            val fileName: String = getAttachmentName(fileInfoMap)
            log.trace { "Set attachment file name $fileName" }
            var fileMimeType = it[NotificationConstants.MIMETYPE] as? String
            log.trace { "Map attachment mimetype $fileMimeType" }
            if (fileMimeType.isNullOrBlank()) {
                val originalExt = fileInfoMap[NotificationConstants.ORIGINAL_EXT]
                log.trace { "Attachment original extension $originalExt" }
                fileMimeType = MimeMappings.DEFAULT.get(originalExt)
                log.trace { "Calculated attachment mimetype $fileMimeType" }
                if (fileMimeType.isNullOrBlank()) {
                    fileMimeType = fileInfoMap[NotificationConstants.MIMETYPE]
                }
            }
            log.trace { "Result attachment mimetype $fileMimeType" }
            if (fileMimeType.isNullOrBlank()) throw NotificationException("Attachment doesn't have mimetype: $it")

            result[fileName] = ByteArrayDataSource(fileBytes, fileMimeType)
        }

        return result
    }

    private fun getAttachmentName(infoAttachment: Map<String, String>): String {
        val fileName = infoAttachment[NotificationConstants.ORIGINAL_NAME]
        log.trace { "Attachment original name '${fileName}'" }
        if (fileName.isNullOrBlank()) throw NotificationException("Attachment doesn't have name: $infoAttachment")
        val fileExt = infoAttachment[NotificationConstants.ORIGINAL_EXT]
        log.trace { "Attachment original ext '${fileExt}'" }
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
