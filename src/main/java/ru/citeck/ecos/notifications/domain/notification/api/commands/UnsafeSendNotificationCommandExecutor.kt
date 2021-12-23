package ru.citeck.ecos.notifications.domain.notification.api.commands

import mu.KotlinLogging
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.DEFAULT_LOCALE
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.notification.predicate.MapElement
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import ru.citeck.ecos.notifications.domain.notification.service.NotificationService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import java.util.*

private const val ECOS_TYPE_ID_KEY = "_etype?id"

@Service
class UnsafeSendNotificationCommandExecutor(
    val notificationService: NotificationService,
    val notificationTemplateService: NotificationTemplateService,
    val predicateService: PredicateService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun execute(command: SendNotificationCommand): SendNotificationResult {
        log.debug { "Execute notification command:\n$command" }

        if (recipientsNotSpecified(command)) {
            log.warn {
                "Notification not sent, no recipients found. " +
                    "Template: ${command.templateRef}, record: ${command.record}"
            }
            return SendNotificationResult(
                NotificationResultStatus.RECIPIENTS_NOT_FOUND.value,
                "Notification not sent, no recipients found"
            )
        }

        val baseTemplate = getTemplateMetaById(command.templateRef.id)

        val template = resolveMultiTemplate(
            baseTemplate = baseTemplate,
            recordEcosTypeId = getRecordEcosTypeByIncomeModel(command.model),
            attributes = MapElement(command.model)
        )

        val requiredModel = mutableMapOf<String, String>()
        baseTemplate.model?.let { requiredModel.putAll(it) }
        if (!Objects.equals(baseTemplate.id, template.id)) {
            template.model?.let { requiredModel.putAll(it) }
        }

        val filledModel = resolveCompletedModel(
            requiredModel = requiredModel,
            incomeFilledModel = command.model
        )

        val locale = if (command.lang.isEmpty()) DEFAULT_LOCALE else LocaleUtils
            .toLocale(command.lang)

        val record = NotificationCommandUtils.resolveNotificationRecord(command.record)

        val notification = RawNotification(
            record = record,
            template = template,
            type = command.type,
            locale = locale,
            recipients = command.recipients,
            model = filledModel,
            from = command.from,
            cc = command.cc,
            bcc = command.bcc
        )

        notificationService.send(notification)

        return SendNotificationResult(NotificationResultStatus.OK.value, "")
    }

    private fun recipientsNotSpecified(command: SendNotificationCommand): Boolean {
        return command.recipients.isEmpty() && command.cc.isEmpty() && command.bcc.isEmpty()
    }

    private fun getTemplateMetaById(id: String): NotificationTemplateWithMeta {
        return notificationTemplateService.findById(id).orElseThrow {
            NotificationException("Template with id: <$id> not found}")
        }
    }

    private fun resolveMultiTemplate(
        baseTemplate: NotificationTemplateWithMeta,
        recordEcosTypeId: String,
        attributes: MapElement
    ): NotificationTemplateWithMeta {
        if (StringUtils.isBlank(recordEcosTypeId)) {
            return baseTemplate
        }

        baseTemplate.multiTemplateConfig?.forEach { it ->
            it.type?.let { typeRef ->
                val checkType = RecordRef.valueOf(recordEcosTypeId) == typeRef
                if (checkType && checkPredicate(it.condition, attributes)) {
                    val template = it.template ?: throw NotificationException(
                        "Multi template ref is null. Base template ref: $baseTemplate"
                    )
                    return resolveMultiTemplate(getTemplateMetaById(template.id), recordEcosTypeId, attributes)
                }
            }
        }

        return baseTemplate
    }

    private fun checkPredicate(condition: Predicate?, attributes: MapElement): Boolean {
        condition?.let {
            return predicateService.isMatch(attributes, it)
        }
        return true
    }

    private fun getRecordEcosTypeByIncomeModel(incomeFilledModel: Map<String, Any>): String {
        val value = incomeFilledModel[ECOS_TYPE_ID_KEY] ?: return ""
        return value.toString()
    }

    private fun resolveCompletedModel(
        requiredModel: Map<String, String>,
        incomeFilledModel: Map<String, Any>
    ): Map<String, Any> {
        val filledModel = mutableMapOf<String, Any>()
        val prefilledModel = incomeFilledModel.toMutableMap()

        requiredModel.forEach { (attrKey, attrValue) ->
            incomeFilledModel[attrValue]?.let {
                filledModel[attrKey] = it
                prefilledModel.remove(attrValue)
            }
        }

        filledModel.putAll(prefilledModel)

        return filledModel
    }

}
