package ru.citeck.ecos.notifications.domain.notification.command

import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.notifications.domain.notification.DEFAULT_LOCALE
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import ru.citeck.ecos.notifications.domain.notification.service.NotificationService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef
import java.util.*

private const val ECOS_TYPE_ID_KEY = "_etype?id"

@Service
class SendNotificationCommandExecutor(
    val notificationService: NotificationService,
    val notificationTemplateService: NotificationTemplateService
) : CommandExecutor<SendNotificationCommand> {

    override fun execute(command: SendNotificationCommand): Any? {
        val baseTemplate = getTemplateMetaById(command.templateRef.id)

        val template = resolveMultiTemplate(
            baseTemplate = baseTemplate,
            recordEcosTypeId = getRecordEcosTypeByIncomeModel(command.model)
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

        val notification = RawNotification(
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

        return SendNotificationResult("ok", "")
    }

    private fun resolveMultiTemplate(baseTemplate: NotificationTemplateWithMeta,
                                     recordEcosTypeId: String): NotificationTemplateWithMeta {
        if (StringUtils.isBlank(recordEcosTypeId)) {
            return baseTemplate
        }

        baseTemplate.multiTemplateConfig?.forEach { it ->
            it.type?.let { typeRef ->
                if (RecordRef.valueOf(recordEcosTypeId) == typeRef) {
                    val template = it.template ?: throw NotificationException(
                        "Multi template ref is null. Base template ref: $baseTemplate"
                    )
                    return getTemplateMetaById(template.id)
                }
            }
        }

        return baseTemplate
    }

    private fun getRecordEcosTypeByIncomeModel(incomeFilledModel: Map<String, Any>): String {
        val value = incomeFilledModel[ECOS_TYPE_ID_KEY] ?: return ""
        return value.toString()
    }

    private fun resolveCompletedModel(requiredModel: Map<String, String>, incomeFilledModel: Map<String, Any>): Map<String, Any> {
        val filledModel = mutableMapOf<String, Any>()
        val prefilledModel = incomeFilledModel.toMutableMap()

        requiredModel.forEach { (attrKey, attrValue) ->
            val cleanAttrValue = attrValue.replaceFirst("\$", "")

            incomeFilledModel[cleanAttrValue]?.let {
                filledModel[attrKey] = it
                prefilledModel.remove(cleanAttrValue)
            }
        }

        filledModel.putAll(prefilledModel)

        return filledModel
    }

    private fun getTemplateMetaById(id: String): NotificationTemplateWithMeta {
        return notificationTemplateService.findById(id).orElseThrow {
            NotificationException("Template with id: <$id> not found}")
        }
    }

}

