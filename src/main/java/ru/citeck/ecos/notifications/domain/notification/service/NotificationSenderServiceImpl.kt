package ru.citeck.ecos.notifications.domain.notification.service

import com.sun.istack.internal.ByteArrayDataSource
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.NotificationConstants
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.notification.isExplicitMsgPayload
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.providers.NotificationProvider
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.element.elematts.RecordAttsElement
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import java.util.*
import java.util.stream.Collectors
import javax.activation.DataSource

@Component
class NotificationSenderServiceImpl(

    @Qualifier("notificationProviders")
    private val providers: Map<NotificationType, List<NotificationProvider>>,

    @Qualifier("notificationSenders")
    private val sendersMap: Map<String, List<NotificationSender<Any>>>,

    private val freemarkerService: FreemarkerTemplateEngineService,

    private val notificationEventService: NotificationEventService,
    private val notificationsSenderService: NotificationsSenderService,
    private val predicateService: PredicateService

) : NotificationSenderService {

    private val log = KotlinLogging.logger {}

    override fun getModel(): Set<String> {
        val activeSenders = notificationsSenderService.getAllEnabled()
        val attributes = activeSenders.filter { it.condition != null }
            .map { PredicateUtils.getAllPredicateAttributes(it.condition) }
            .filter { it.isNotEmpty() }.stream()
            .flatMap { value -> value.stream() }.collect(Collectors.toList())
        return attributes.toSet()
    }

    override fun sendNotification(notification: RawNotification): NotificationSenderSendStatus {
        log.debug("Send notification raw $notification")
        //выборка из дао по типу нотификации с сортировкой по порядку
        val senders = notificationsSenderService.getEnabled(
            Predicates.eq(NotificationsSenderEntity.PROP_NOTIFICATION_TYPE, notification.type), null
        )
        if (senders.isEmpty()) {
            throw NotificationException("Failed to find notifications sender for type ${notification.type}")
        }
        val fitNotification = convertRawNotificationToFit(notification)
        senders.forEach {
            if (it.senderType == null) {
                log.warn { "Sender type is undefined at #${it.id} notifications sender" }
                return@forEach
            }
            if (it.templates.isNotEmpty() && notification.template != null) {
                var acceptable = false
                for (recordRef in it.templates) {
                    if (it.id == notification.template.id) {
                        acceptable = true
                        break
                    }
                }
                if (!acceptable) {
                    log.debug { "Sender #${it.id} does not fit for notification by template" }
                    return@forEach
                }
            }
            if (it.condition != null) {
                val atts = ObjectData.create();
                notification.model.forEach { (attName, value) -> atts.add(attName, value) }
                val recAtts = RecordAtts()
                recAtts.setAtts(atts)
                val element = RecordAttsElement("", recAtts)
                val acceptable = predicateService.isMatch(element, it.condition!!)
                if (!acceptable) {
                    log.debug { "Sender #${it.id} does not fit for notification by condition ${it.condition}" }
                    return@forEach
                }
            }
            //для каждой записи ищем в notificationSenders соответствующий типу бин
            val senderBeanList = sendersMap[it.senderType]
                ?: throw NotificationException(
                    "Failed to find sender implementation for type ${it.senderType} " +
                        "for #${it.id} notifications sender"
                )
            val notificationEventDto = NotificationEventDto(
                rec = notification.record,
                notificationType = notification.type,
                notification = fitNotification,
                model = notification.model
            )
            //у бина вызываем sendNotification с конфигурацией
            for (senderBean in senderBeanList) {
                if (senderBean.getNotificationType() != it.notificationType) {
                    continue
                }
                log.debug { "Send notification through sender ${it.id} with type ${it.senderType}" }
                /*senderBean.javaClass.interfaces
                    .filter { type -> type.javaClass.equals(NotificationSender::class) }
                    .first().typeParameters*/
                val configClass = senderBean.getConfigClass()
                var result: NotificationSenderSendStatus? = null
                try {
                    result = senderBean.sendNotification(fitNotification, it.senderConfig.getAs(configClass))
                    log.debug { "Send result is $result" }
                } catch (e: NotificationException){
                    log.error("Failed to send notification through sender ${it.id} \n ${e.message}", e)
                    notificationEventService.emitSendFailure(notificationEventDto)
                    continue
                }
                if (NotificationSenderSendStatus.SKIPPED.equals(result)) {
                    continue
                } else {
                    when (result) {
                        NotificationSenderSendStatus.SENT ->
                            notificationEventService.emitSendSuccess(notificationEventDto)
                        NotificationSenderSendStatus.BLOCKED ->
                            notificationEventService.emitSendBlocked(notificationEventDto)
                    }
                    return result
                }
            }
        }
        throw NotificationException("Failed to send notification")
        // Так же этот статус отправляется в таблицу нотификаций (см. NotificationState)
    }

    fun send(rawNotification: RawNotification) {
        log.debug("Send notification raw: $rawNotification")

        val fitNotification = convertRawNotificationToFit(rawNotification)

        val foundProviders = providers[rawNotification.type]
            ?: throw NotificationException("Provider with notification type: ${rawNotification.type} not registered}")

        foundProviders.forEach {
            it.send(fitNotification)

            notificationEventService.emitSendSuccess(
                NotificationEventDto(
                    rec = rawNotification.record,
                    notificationType = rawNotification.type,
                    notification = fitNotification,
                    model = rawNotification.model
                )
            )
        }
    }

    private fun convertRawNotificationToFit(rawNotification: RawNotification): FitNotification {
        val title = if (rawNotification.isExplicitMsgPayload()) {
            rawNotification.title
        } else {
            prepareTitle(rawNotification.template!!, rawNotification.locale, rawNotification.model)
        }
        val body = if (rawNotification.isExplicitMsgPayload()) {
            rawNotification.body
        } else {
            prepareBody(rawNotification.template!!, rawNotification.locale, rawNotification.model)
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
            data = data
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
        if (model[NotificationConstants.DATA] == null) {
            return emptyMap()
        }

        return model[NotificationConstants.DATA] as Map<String, Any>
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
