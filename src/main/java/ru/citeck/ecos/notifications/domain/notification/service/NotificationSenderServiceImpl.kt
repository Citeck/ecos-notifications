package ru.citeck.ecos.notifications.domain.notification.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.activation.DataSource
import jakarta.mail.util.ByteArrayDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.server.MimeMappings
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.notification.*
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.domain.template.api.records.NOTIFICATION_TEMPLATE_RECORD_ID
import ru.citeck.ecos.notifications.domain.template.constants.DefaultTplModelAtts
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService
import ru.citeck.ecos.notifications.freemarker.TemplateProcCtxKey
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus.*
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.element.elematts.RecordAttsElement
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

@Component
class NotificationSenderServiceImpl(

    @Qualifier("notificationSenders")
    private val sendersMap: Map<String, List<NotificationSender<Any>>>,

    private val freemarkerService: FreemarkerTemplateEngineService,

    private val notificationEventService: NotificationEventService,
    private val notificationsSenderService: NotificationsSenderService,
    private val predicateService: PredicateService,

    private val ecosContext: EcosContext

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
        log.debug { "Send notification raw $notification" }

        val senders = notificationsSenderService.getEnabled(
            Predicates.eq(NotificationsSenderEntity.PROP_NOTIFICATION_TYPE, notification.type),
            null
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
                    if (recordRef.getLocalId() == notification.template.id) {
                        acceptable = true
                        break
                    }
                }
                if (!acceptable) {
                    log.debug { "Sender '${sender.id}' does not fit for notification by template" }
                    return@forEach
                }
            }

            if (sender.condition != null && sender.condition != VoidPredicate.INSTANCE) {
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
                model = notification.model,
                sendingMeta = emptyMap()
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
                val eventDtoWithResultMeta = notificationEventDto.copy(sendingMeta = result.meta)

                when (result.status) {
                    SENT -> notificationEventService.emitSendSuccess(eventDtoWithResultMeta)
                    BLOCKED -> notificationEventService.emitSendBlocked(eventDtoWithResultMeta)
                    SKIPPED -> continue
                }
                return result.status
            }
        }

        throw NotificationException("Failed to send notification. None of the senders returned a result")
    }

    private fun convertRawNotificationToFit(rawNotification: RawNotification): FitNotification {
        val ignoreTemplate = parseIgnoreTemplateFlag(rawNotification)

        val title = if (rawNotification.isExplicitMsgPayload() || ignoreTemplate) {
            rawNotification.title
        } else {
            prepareTitle(rawNotification.template!!, rawNotification.locale, rawNotification.model)
        }

        val augmentedModel = rawNotification.model.toMutableMap()
        val systemNotificationMeta = mapOf(
            NOTIFICATION_SYS_META_TITLE_ATT to title,
            NOTIFICATION_SYS_META_FROM_ATT to rawNotification.from,
            NOTIFICATION_SYS_META_TO_ATT to rawNotification.recipients,
            NOTIFICATION_SYS_META_CC_ATT to rawNotification.cc,
            NOTIFICATION_SYS_META_BCC_ATT to rawNotification.bcc,
            NOTIFICATION_SYS_META_WEB_URL_ATT to rawNotification.webUrl
        )
        augmentedModel[NOTIFICATION_SYS_META_ATT] = systemNotificationMeta

        val body = if (rawNotification.isExplicitMsgPayload() || ignoreTemplate) {
            rawNotification.body
        } else {
            prepareBody(rawNotification.template!!, rawNotification.locale, augmentedModel, rawNotification.webUrl)
        }

        val attachments = prepareAttachments(augmentedModel)
        val data = prepareData(augmentedModel)

        return FitNotification(
            title = title,
            body = body,
            recipients = rawNotification.recipients,
            from = rawNotification.from,
            cc = rawNotification.cc,
            bcc = rawNotification.bcc,
            webUrl = rawNotification.webUrl,
            attachments = attachments,
            data = data,
            templateRef = rawNotification.template?.let {
                EntityRef.create(AppName.NOTIFICATIONS, NOTIFICATION_TEMPLATE_RECORD_ID, rawNotification.template.id)
            }
        )
    }

    private fun parseIgnoreTemplateFlag(rawNotification: RawNotification): Boolean {
        var ignoreTemplate = false

        rawNotification.model[NOTIFICATION_DATA]?.let { data ->
            @Suppress("UNCHECKED_CAST")
            val dataMap: Map<String, Any> = data as Map<String, Any>
            dataMap[NOTIFICATION_IGNORE_TEMPLATE]?.let {
                ignoreTemplate = dataMap[NOTIFICATION_IGNORE_TEMPLATE].toString().toBoolean()
            }
        }

        return ignoreTemplate
    }

    private fun prepareBody(
        template: NotificationTemplateWithMeta,
        locale: Locale,
        model: Map<String, Any?>,
        webUrl: String
    ): String {
        return ecosContext.newScope().use { scope ->
            scope[TemplateProcCtxKey.WORKSPACE] = model[DefaultTplModelAtts.ATT_WORKSPACE] as? String ?: ""
            scope[TemplateProcCtxKey.CUSTOM_WEB_URL] = webUrl

            freemarkerService.process(template.id, locale, model)
        }
    }

    private fun prepareTitle(template: NotificationTemplateWithMeta, locale: Locale, model: Map<String, Any?>): String {
        val title = template.notificationTitle ?: return ""

        val titleTemplate = resolveAnyAvailableTitle(title, locale)
            ?: throw NotificationException("Notification title not found in template: $template")

        return freemarkerService.process(template.id + "_title", titleTemplate, model)
    }

    @Suppress("UNCHECKED_CAST")
    private fun prepareData(model: Map<String, Any?>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        result[NOTIFICATION_MODEL] = model.toMap()
        if (model[NOTIFICATION_DATA] == null) {
            return result
        }
        result.putAll(model[NOTIFICATION_DATA] as Map<String, Any?>)
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun prepareAttachments(model: Map<String, Any?>): Map<String, DataSource> {

        val attachments = model[NOTIFICATION_ATTACHMENTS] as? List<Map<String, Any>>
            ?: (model[NOTIFICATION_ATTACHMENTS] as? Map<String, Any>)?.let { attach ->
                if (attach.isEmpty()) {
                    emptyList()
                } else {
                    listOf(attach)
                }
            }
            ?: listOf()

        val result = mutableMapOf<String, DataSource>()

        attachments.forEach { attachment ->
            val contentStr = attachment[NOTIFICATION_ATTACHMENT_BYTES] as? String
            if (contentStr.isNullOrBlank()) {
                throw NotificationException("Attachment doesn't have content: $attachment")
            }

            val fileBytes: ByteArray = Base64.getMimeDecoder().decode(contentStr)

            val fileMeta: Map<String, String> = let {
                if (attachment.containsKey(NOTIFICATION_ATTACHMENT_META)) {
                    attachment[NOTIFICATION_ATTACHMENT_META] as Map<String, String>
                } else {
                    attachment[NOTIFICATION_ATTACHMENTS_PREVIEW_INFO] as Map<String, String>
                }
            }
            log.trace { "Attachment meta:\n $fileMeta" }

            val fileName: String = getAttachmentName(fileMeta)
            log.trace { "Set attachment file name $fileName" }

            val fileMimeType = let {
                val mimeType = fileMeta.getAnyNotBlank(NOTIFICATION_ATTACHMENT_MIMETYPE_ATTS)
                log.trace { "Map attachment mimetype $mimeType" }

                if (mimeType.isNullOrBlank()) {
                    val originalExt = fileMeta.getAnyNotBlank(NOTIFICATION_ATTACHMENT_EXT_ATTS)
                    log.trace { "Attachment extension $originalExt" }
                    MimeMappings.DEFAULT.get(originalExt) ?: mimeType
                } else {
                    mimeType
                }
            }

            log.trace { "Result attachment mimetype $fileMimeType" }
            if (fileMimeType.isNullOrBlank()) {
                throw NotificationException("Attachment doesn't have mimetype: $attachment")
            }

            result[fileName] = ByteArrayDataSource(fileBytes, fileMimeType)
        }

        return result
    }

    private fun getAttachmentName(attachmentMeta: Map<String, String>): String {
        val fileName = attachmentMeta.getAnyNotBlank(NOTIFICATION_ATTACHMENT_NAME_ATTS)
        log.debug { "Attachment name '$fileName'" }

        if (fileName.isNullOrBlank()) {
            throw NotificationException("Attachment doesn't have name: $attachmentMeta")
        }

        val fileExt = attachmentMeta.getAnyNotBlank(NOTIFICATION_ATTACHMENT_EXT_ATTS)
        log.debug { "Attachment ext '$fileExt'" }

        if (fileExt.isNullOrBlank()) {
            throw NotificationException("Attachment doesn't have ext: $attachmentMeta")
        }

        return if (fileExt == fileName.takeLast(fileExt.length)) {
            fileName
        } else {
            fileName.plus(".").plus(fileExt)
        }
    }

    private fun Map<String, String>.getAnyNotBlank(keys: List<String>): String? {
        for (key in keys) {
            val value = this[key]
            if (value is String && value.isNotBlank()) {
                return value
            }
        }
        return null
    }

    private fun resolveAnyAvailableTitle(titleMl: MLText, locale: Locale): String? {
        val result = MLText.getClosestValue(titleMl, locale)
        return result.ifBlank {
            null
        }
    }
}
