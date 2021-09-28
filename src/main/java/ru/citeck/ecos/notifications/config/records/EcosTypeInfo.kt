package ru.citeck.ecos.notifications.config.records

import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.records2.RecordRef

data class EcosTypeInfo(

    val id: String,
    val name: MLText,
    val description: MLText,

    val system: Boolean,

    val sourceId: String,
    val metaRecord: RecordRef?,

    val parentRef: RecordRef?,
    val formRef: RecordRef?,
    val journalRef: RecordRef?,

    val dashboardType: String,

    val inheritForm: Boolean,
    val inheritActions: Boolean,
    val inheritNumTemplate: Boolean,

    val dispNameTemplate: MLText,
    val numTemplateRef: RecordRef?,

    val actions: List<RecordRef>,

    /* create */
    val defaultCreateVariant: Boolean?,
    val createVariants: List<CreateVariantDef>,
    val postCreateActionRef: RecordRef?,

    /* config */
    val configFormRef: RecordRef?,
    val config: ObjectData,

    val model: TypeModelDef,
    val docLib: DocLibDef,

    val properties: ObjectData
)
