package ru.citeck.ecos.notifications.domain.notification.service

import freemarker.core.ParseException
import freemarker.template.MalformedTemplateNameException
import freemarker.template.TemplateException
import freemarker.template.TemplateNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.activation.DataSource
import jakarta.mail.util.ByteArrayDataSource
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.server.MimeMappings
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.notification.*
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderResult
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.domain.template.api.records.NOTIFICATION_TEMPLATE_RECORD_ID
import ru.citeck.ecos.notifications.domain.template.constants.DefaultTplModelAtts
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService
import ru.citeck.ecos.notifications.freemarker.TemplateProcCtxKey
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
    private val workspaceService: WorkspaceService,

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

    override fun sendNotification(notification: RawNotification): NotificationSenderResult {
        log.debug { "Send notification raw $notification" }

        val senders = notificationsSenderService.getEnabled(
            Predicates.eq(NotificationsSenderEntity.PROP_NOTIFICATION_TYPE, notification.type),
            null
        )
        if (senders.isEmpty()) {
            // Deliberately transient (plain NotificationException): the sender artifact may be
            // deployed later, and retrying lets queued notifications go out once it appears.
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
                val notificationTemplateRefLocalId = workspaceService.addWsPrefixToId(
                    notification.template.id,
                    notification.template.workspace
                )
                for (recordRef in sender.templates) {
                    if (recordRef.getLocalId() == notificationTemplateRefLocalId) {
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
                // the config body is deliberately left out of both messages: they are persisted in
                // notification.error_message and shown in the UI, while a sender config holds
                // channel credentials (SMTP/Firebase). The sender id and the target class are
                // enough to find the broken artifact
                val config = try {
                    sender.senderConfig.getAs(configClass)
                } catch (e: Exception) {
                    throw NotificationPermanentException(
                        "Failed to parse config of '${sender.id}' notifications sender " +
                            "(type '${sender.senderType}') as class ${configClass.name}",
                        e
                    )
                } ?: throw NotificationPermanentException(
                    "Failed to get config of '${sender.id}' notifications sender " +
                        "(type '${sender.senderType}') as class ${configClass.name}"
                )

                val result = senderBean.sendNotification(fitNotification, config)
                val eventDtoWithResultMeta = notificationEventDto.copy(sendingMeta = result.meta)

                when (result.status) {
                    SENT -> notificationEventService.emitSendSuccess(eventDtoWithResultMeta)
                    BLOCKED -> notificationEventService.emitSendBlocked(eventDtoWithResultMeta)
                    SKIPPED -> continue
                }
                return result
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
                val localId = workspaceService.addWsPrefixToId(
                    rawNotification.template.id,
                    rawNotification.template.workspace
                )
                EntityRef.create(AppName.NOTIFICATIONS, NOTIFICATION_TEMPLATE_RECORD_ID, localId)
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

            val templateKey = workspaceService.addWsPrefixToId(template.id, template.workspace)
            wrapTemplateDefects(templateKey) {
                freemarkerService.process(templateKey, locale, model)
            }
        }
    }

    private fun prepareTitle(template: NotificationTemplateWithMeta, locale: Locale, model: Map<String, Any?>): String {
        val title = template.notificationTitle ?: return ""

        val titleTemplate = resolveAnyAvailableTitle(title, locale)
            ?: throw NotificationPermanentException("Notification title not found in template: $template")

        val templateKey = workspaceService.addWsPrefixToId(template.id, template.workspace) + "_title"
        return wrapTemplateDefects(templateKey) {
            freemarkerService.process(templateKey, titleTemplate, model)
        }
    }

    /**
     * Template defects (bad markup, references to missing model attributes) cannot be fixed
     * by retrying — they surface as [TemplateException] (render time) or [ParseException]
     * (parse time; extends IOException but is a defect, not an IO failure) and are marked
     * permanent. True IO/loading errors are left as-is and stay transient.
     *
     * [TemplateNotFoundException] / [MalformedTemplateNameException] are defects too, even though
     * they are IOExceptions: [ru.citeck.ecos.notifications.freemarker.EcosTemplateLoader] returns
     * null only when the name resolves to no template at all — a typo'd `<#import>`/`<#include>`
     * target is the realistic case. A DB outage inside the loader propagates as a thrown exception
     * instead, whose root is then a SQL failure and stays transient. This matches the verdict a
     * missing template row already gets in
     * [ru.citeck.ecos.notifications.domain.notification.api.commands.UnsafeSendNotificationCommandExecutor];
     * without it the very defect the retry redesign targets (broken template → exactly one attempt)
     * would burn the whole retry budget.
     *
     * The verdict is taken from the ROOT of the cause chain, not from the first [TemplateException]
     * in it: FreeMarker wraps whatever an injected bean throws into a [TemplateException] too
     * (`EcosConfigAccessor` calls the config service, `ImageAccessor` reads files from the DB),
     * and such a failure is a transient outage, not a broken template. Keeping it transient
     * follows the classification asymmetry — a false-permanent verdict loses mail forever,
     * a false-transient one costs a few cheap attempts.
     */
    private fun <T> wrapTemplateDefects(templateKey: String, render: () -> T): T {
        try {
            return render()
        } catch (e: Exception) {
            val root = ExceptionUtils.getThrowableList(e).last()
            val defect = root is TemplateException ||
                root is ParseException ||
                root is TemplateNotFoundException ||
                root is MalformedTemplateNameException
            if (defect) {
                throw NotificationPermanentException(
                    "Failed to render notification template '$templateKey'",
                    e
                )
            }
            throw e
        }
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
