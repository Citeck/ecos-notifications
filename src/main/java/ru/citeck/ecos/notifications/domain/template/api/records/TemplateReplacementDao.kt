package ru.citeck.ecos.notifications.domain.template.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.StringUtils
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef

const val NOTIFICATION_TEMPLATE_REPLACEMENT_ID = "multi-template-config"

@Component
class TemplateReplacementDao(private val recordService: RecordsService) :
    AbstractRecordsDao(), RecordsDeleteDao, RecordsQueryDao,
    RecordMutateDtoDao<TemplateReplacementDao.Replacement> {

    companion object {
        private const val PARENT = "parentTemplate"
        private const val TYPE = "typeValue"
        private const val REPLACEMENT = "replacement"
        private const val MULTI_TEMPLATE_CONFIG = "multiTemplateConfig"
        private const val MULTI_CONFIG = "$MULTI_TEMPLATE_CONFIG[]?json"
        private const val DELIMITER = "$"
        private const val PARENT_TEMPLATE_PART = 0
        private const val TYPE_PART = 1
        private const val REPLACEMENT_PART = 2
        private val ESCAPER = NameUtils.getEscaper("\$")
    }

    override fun getId(): String {
        return NOTIFICATION_TEMPLATE_REPLACEMENT_ID
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        val result = ArrayList<DelStatus>()
        if (recordIds.isEmpty()) {
            return result
        }
        val firstRecordId = recordIds.first()
        val firstIdParts = firstRecordId.split(DELIMITER)
        if (firstIdParts.size != 3) {
            throw IllegalArgumentException("Invalid replacement record ID: $firstRecordId")
        }
        val parentTemplate = ESCAPER.unescape(firstIdParts[PARENT_TEMPLATE_PART])
        var templateConfig = recordService.getAtt(parentTemplate, MULTI_CONFIG)
                .asList(MultiTemplateElementDto::class.java)
        templateConfig = filterConfig(templateConfig, firstIdParts)
        result.add(DelStatus.OK)
        for (idx in 1 until recordIds.size) {
            val idParts = recordIds[idx].split(DELIMITER)
            if (firstIdParts.size != 3) {
                throw IllegalArgumentException("Invalid replacement record ID: ${recordIds[idx]}")
            }
            templateConfig = filterConfig(templateConfig, idParts)
            result.add(DelStatus.OK)
        }
        recordService.mutate(parentTemplate, ObjectData.create().set(MULTI_TEMPLATE_CONFIG, templateConfig))
        return result
    }

    private fun filterConfig(
        list: MutableList<MultiTemplateElementDto>,
        parts: List<String>
    ): MutableList<MultiTemplateElementDto> {
        return list.filter {
            if (it.type != null) {
                it.type.toString() != ESCAPER.unescape(parts[TYPE_PART]) ||
                    it.template == null ||
                    it.template.toString() != ESCAPER.unescape(parts[REPLACEMENT_PART])
            } else {
                true
            }
        }.toMutableList()
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        var parentTemplateString: String? = ""
        val typeFilterValue: MutableList<String> = mutableListOf()
        val replacementFilterValue: MutableList<String> = mutableListOf()
        PredicateUtils.filterValuePredicates(recsQuery.getPredicate()) { valuePredicate ->
            if (valuePredicate.getAttribute() == PARENT) {
                parentTemplateString = valuePredicate.getValue().asText()
            } else if (valuePredicate.getAttribute() == TYPE) {
                typeFilterValue.add(valuePredicate.getValue().asText())
            } else if (valuePredicate.getAttribute() == REPLACEMENT) {
                replacementFilterValue.add(valuePredicate.getValue().asText())
            }
            true
        }
        if (StringUtils.isNotBlank(parentTemplateString)) {
            val parentTemplate = EntityRef.valueOf(parentTemplateString)
            if (parentTemplate.isEmpty()) {
                throw IllegalArgumentException("Parent template must be defined in request $recsQuery")
            }
            val templateConfig = recordService.getAtt(parentTemplate, MULTI_CONFIG)
                    .asList(MultiTemplateElementDto::class.java)
            var resultRecs = templateConfig
                .sortedWith(compareBy { it.type?.getLocalId() })
                .map { Replacement(parentTemplate, it.template, it.type, it.condition) }
                .toList()
            if (typeFilterValue.isNotEmpty()) {
                resultRecs = resultRecs.filter { typeFilterValue.contains(it.typeValue.toString()) }
                    .toList()
            }
            if (replacementFilterValue.isNotEmpty()) {
                resultRecs = resultRecs.filter { replacementFilterValue.contains(it.replacement.toString()) }
                    .toList()
            }
            val result = RecsQueryRes<Replacement>()
            result.setTotalCount(resultRecs.size.toLong())
            val startIdx = recsQuery.page.skipCount
            if (startIdx >= resultRecs.size) {
                result.setRecords(emptyList())
                return result
            }
            var maxItems = recsQuery.page.maxItems
            if (maxItems <= 0){
                maxItems = 100
            }
            var endIdx = startIdx + maxItems
            endIdx = if (endIdx <= resultRecs.size) endIdx else resultRecs.size
            val checkRange = resultRecs.slice(startIdx..<endIdx)
            result.setRecords(checkRange)
            return result
        } else {
            throw IllegalArgumentException("Parent template was not defined in request $recsQuery")
        }
    }

    override fun getRecToMutate(recordId: String): Replacement {
        return Replacement()
    }

    override fun saveMutatedRec(record: Replacement): String {
        val parentTemplate: EntityRef? = record.parentTemplate
        if (parentTemplate == null || parentTemplate.isEmpty()) {
            throw IllegalArgumentException("Parent template must be defined in replacement creation request")
        }
        if (record.replacement == null || record.replacement!!.isEmpty() ||
            record.typeValue == null || record.typeValue!!.isEmpty()) {
            throw IllegalArgumentException("Invalid replacement record data: $record")
        }
        val templateConfig = recordService.getAtt(parentTemplate, MULTI_CONFIG)
            .asList(MultiTemplateElementDto::class.java)
        templateConfig.removeIf {
            it.type == record.typeValue && it.template == record.replacement
        }
        templateConfig.add(MultiTemplateElementDto(record.replacement, record.typeValue, record.condition))
        recordService.mutate(parentTemplate, ObjectData.create().set(MULTI_TEMPLATE_CONFIG, templateConfig))
        return record.id
    }

    class Replacement() {

        constructor(
            parentTemplate: EntityRef, replacement: EntityRef?,
            typeValue: EntityRef?, condition: Predicate?
        ) : this() {
            this.parentTemplate = parentTemplate
            this.replacement = replacement
            this.typeValue = typeValue
            this.condition = condition
        }

        @get:AttName("id")
        val id: String
            get() = "${NotificationsApp.NAME}/$NOTIFICATION_TEMPLATE_REPLACEMENT_ID@" +
                "${ESCAPER.escape(parentTemplate?.toString() ?: "")}$DELIMITER${ESCAPER.escape(typeValue?.toString() ?: "")}" +
                "$DELIMITER${ESCAPER.escape(replacement?.toString() ?: "")}"

        var typeValue: EntityRef? = null
        var replacement: EntityRef? = null
        var condition: Predicate? = null
        var parentTemplate: EntityRef? = null

        override fun toString(): String {
            return "Replacement(" +
                "parentTemplate='$parentTemplate', " +
                "typeValue='$typeValue', " +
                "replacement='$replacement', " +
                "condition='$condition')"
        }
    }
}
