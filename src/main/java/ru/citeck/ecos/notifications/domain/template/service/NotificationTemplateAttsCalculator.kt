package ru.citeck.ecos.notifications.domain.template.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.template.api.records.NOTIFICATION_TEMPLATE_RECORD_ID
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.api.GetTemplateDataExactTemplate
import ru.citeck.ecos.notifications.lib.api.GetTemplateDataRes
import ru.citeck.ecos.notifications.lib.api.GetTemplateDataTemplateVariants
import ru.citeck.ecos.notifications.lib.api.NotificationsAppApi
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Service
class NotificationTemplateAttsCalculator(
    val notificationTemplateService: NotificationTemplateService,
    val recordsService: RecordsService,
    val sendersService: NotificationSenderService
) : NotificationsAppApi {

    companion object {
        private const val ATT_TYPE_ID = "_type?id"
    }

    private fun findRequiredTemplate(id: String): NotificationTemplateDto {
        return notificationTemplateService.findById(id).orElseThrow {
            RuntimeException("Notification template does not exist: '$id'")
        }
    }

    private fun getTemplateDataForExactTemplate(
        baseTemplate: NotificationTemplateDto?,
        templateRef: EntityRef
    ): GetTemplateDataExactTemplate {
        return getTemplateDataForExactTemplate(baseTemplate, findRequiredTemplate(templateRef.getLocalId()))
    }

    private fun getTemplateDataForExactTemplate(
        baseTemplate: NotificationTemplateDto?,
        template: NotificationTemplateDto
    ): GetTemplateDataExactTemplate {
        val requiredAtts = HashSet<String>()
        baseTemplate?.model?.forEach { (_, v) -> requiredAtts.add(v) }
        template.model?.forEach { (_, v) -> requiredAtts.add(v) }
        requiredAtts.addAll(sendersService.getModel())
        val templateRef = EntityRef.create(
            AppName.NOTIFICATIONS,
            NOTIFICATION_TEMPLATE_RECORD_ID,
            template.id
        )
        return GetTemplateDataExactTemplate(templateRef, requiredAtts)
    }

    override fun getTemplateData(
        templateRef: EntityRef,
        attributes: ObjectData,
        contextData: DataValue
    ): GetTemplateDataRes {

        val template = findRequiredTemplate(templateRef.getLocalId())
        val multiTemplates = template.multiTemplateConfig
        if (multiTemplates.isNullOrEmpty()) {
            return getTemplateDataForExactTemplate(null, template)
        }
        val typeRef = attributes[ATT_TYPE_ID].asText().toEntityRef()
        val applicableMultiTemplates = ArrayList<GetTemplateDataTemplateVariants.Variant>()
        for (multiTemplate in multiTemplates) {
            val subTemplateRef = multiTemplate.template ?: continue
            if (multiTemplate.type == typeRef && subTemplateRef.isNotEmpty()) {
                val condition = multiTemplate.condition
                if (applicableMultiTemplates.isEmpty() &&
                    (condition == null || PredicateUtils.isAlwaysTrue(condition))
                ) {

                    return getTemplateDataForExactTemplate(template, subTemplateRef)
                } else {
                    applicableMultiTemplates.add(
                        GetTemplateDataTemplateVariants.Variant(
                            subTemplateRef,
                            condition ?: Predicates.alwaysTrue()
                        )
                    )
                }
            }
        }
        if (applicableMultiTemplates.isEmpty()) {
            return getTemplateDataForExactTemplate(null, templateRef)
        }
        applicableMultiTemplates.add(
            GetTemplateDataTemplateVariants.Variant(templateRef, Predicates.alwaysTrue())
        )

        return GetTemplateDataTemplateVariants(
            requiredAtts = template.model?.values?.toSet() ?: emptySet(),
            variants = applicableMultiTemplates,
            contextData = contextData
        )
    }

    /**
     * Calculate all required attributes for template include all sub-templates.
     * This is not an optimal way to work with model attributes
     * prefer to use getTemplateData
     */
    fun getAllRequiredAtts(templateData: NotificationTemplateDto): Set<String> {

        val attributes = mutableSetOf<String>()

        templateData.model?.forEach { (_, dataValue) -> attributes.add(dataValue) }
        addAttributesRecursive(templateData.multiTemplateConfig, attributes)
        attributes.addAll(sendersService.getModel())

        return attributes
    }

    private fun addAttributesRecursive(
        multiTemplateConfig: List<MultiTemplateElementDto>?,
        attributes: MutableSet<String>
    ) {
        multiTemplateConfig?.forEach { element ->
            element.condition?.let {
                val allPredicateAttributes = PredicateUtils.getAllPredicateAttributes(it)
                attributes.addAll(allPredicateAttributes)
            }

            element.template?.let {
                if (it.getLocalId().isNotEmpty()) {
                    val data = recordsService.getAtts(EntityRef.valueOf(it), SubTemplateAtts::class.java)
                    data.model.forEach { (_, att) -> attributes.add(att) }
                    addAttributesRecursive(data.multiTemplateConfig, attributes)
                }
            }
        }
    }

    private class SubTemplateAtts(
        @AttName("model?json!")
        val model: Map<String, String>,
        val multiTemplateConfig: List<MultiTemplateElementDto>?
    )
}
