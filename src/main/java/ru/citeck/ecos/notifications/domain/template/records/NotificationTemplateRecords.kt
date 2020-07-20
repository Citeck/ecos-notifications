package ru.citeck.ecos.notifications.domain.template.records

import ecos.com.fasterxml.jackson210.annotation.JsonProperty
import ecos.com.fasterxml.jackson210.annotation.JsonValue
import org.apache.commons.lang.StringUtils
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.getLangKeyFromFileName
import ru.citeck.ecos.notifications.domain.template.records.NotificationTemplateRecords.NotTemplateRecord
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordMeta
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.request.delete.RecordsDelResult
import ru.citeck.ecos.records2.request.delete.RecordsDeletion
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import ru.citeck.ecos.records2.request.query.SortBy
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao
import java.util.*
import java.util.stream.Collectors

@Component
class NotificationTemplateRecords(templateService: NotificationTemplateService) : LocalRecordsDao(),
    LocalRecordsQueryWithMetaDao<NotTemplateRecord>,
    LocalRecordsMetaDao<NotTemplateRecord>,
    MutableRecordsLocalDao<NotTemplateRecord> {

    private val templateService: NotificationTemplateService

    override fun delete(deletion: RecordsDeletion): RecordsDelResult {
        val result = RecordsDelResult()
        for (record in deletion.records) {
            templateService.deleteById(record.id)
            result.addRecord(RecordMeta(record))
        }
        return result
    }

    override fun getValuesToMutate(records: List<RecordRef>): List<NotTemplateRecord> {
        return getLocalRecordsMeta(records, EmptyMetaField.INSTANCE)
    }

    override fun save(values: List<NotTemplateRecord>): RecordsMutResult {
        val result = RecordsMutResult()
        val savedList = values.stream()
            .map { dto: NotTemplateRecord? -> templateService.save(dto) }
            .map(NotificationTemplateDto::id)
            .map { id: String? -> RecordMeta(id) }
            .collect(Collectors.toList())
        result.records = savedList
        return result
    }

    override fun getLocalRecordsMeta(records: List<RecordRef>, metaField: MetaField): List<NotTemplateRecord> {
        return records.stream()
            .map { obj: RecordRef -> obj.id }
            .map { id: String? ->
                templateService.findById(id)
                    .orElseGet { NotificationTemplateDto(id!!) }
            }
            .map { dto: NotificationTemplateDto -> NotTemplateRecord(dto) }
            .collect(Collectors.toList())
    }

    override fun queryLocalRecords(recordsQuery: RecordsQuery, metaField: MetaField): RecordsQueryResult<NotTemplateRecord> {
        val records = RecordsQueryResult<NotTemplateRecord>()
        var max = recordsQuery.maxItems
        if (max <= 0) {
            max = 10000
        }
        val skip = recordsQuery.skipCount
        if ("predicate" == recordsQuery.language) {
            val predicate = recordsQuery.getQuery(Predicate::class.java)
            val order = recordsQuery.sortBy
                .stream()
                .filter { s: SortBy -> RecordConstants.ATT_MODIFIED == s.attribute }
                .map { s: SortBy ->
                    var attribute = s.attribute
                    if (RecordConstants.ATT_MODIFIED == attribute) {
                        attribute = "lastModifiedDate"
                    }
                    if (s.isAscending) Sort.Order.asc(attribute) else Sort.Order.desc(attribute)
                }
                .collect(Collectors.toList())
            val types: Collection<NotTemplateRecord> = templateService.getAll(
                max,
                recordsQuery.skipCount,
                predicate,
                if (order.isNotEmpty()) Sort.by(order) else null
            )
                .stream()
                .map { dto: NotificationTemplateDto -> NotTemplateRecord(dto) }
                .collect(Collectors.toList())
            records.records = ArrayList(types)
            records.totalCount = templateService.getCount(predicate)
            return records
        }
        if ("criteria" == recordsQuery.language) {
            records.records = templateService.getAll(max, skip)
                .stream()
                .map { dto: NotificationTemplateDto -> NotTemplateRecord(dto) }
                .collect(Collectors.toList())
            records.totalCount = templateService.count
            return records
        }
        return RecordsQueryResult()
    }


    //TODO: fix uplaod from json in journal

    data class NotTemplateRecord(val dto: NotificationTemplateDto) : NotificationTemplateDto(dto) {
        var moduleId: String
            get() = id
            set(value) {
                id = value
            }

        @get:MetaAtt(".type")
        val ecosType: RecordRef
            get() = RecordRef.create("emodel", "type", "notification-template")

        @get:MetaAtt(".disp")
        val displayName: String?
            get() = name

        @JsonProperty("templateContent")
        fun setContent(content: List<ObjectData>) {
            val updatedData: MutableMap<String, TemplateDataDto> = data.toMutableMap()

            content.forEach {
                var base64Content = it.get("url", "")
                base64Content = StringUtils.substringAfter(base64Content, ",")

                val contentBytes = Base64.getDecoder().decode(base64Content.toByteArray(Charsets.UTF_8))
                val fileName = it.get("originalName").asText()

                val langKey = getLangKeyFromFileName(fileName)

                val templateData = TemplateDataDto(fileName, contentBytes)

                updatedData[langKey] = templateData
            }

            this.data = updatedData
        }

        @JsonValue
        @com.fasterxml.jackson.annotation.JsonValue
        fun toJson(): NotificationTemplateDto? {
            return NotificationTemplateDto(this)
        }
    }

    companion object {
        const val ID = "template"
    }

    init {
        id = ID
        this.templateService = templateService
    }
}
