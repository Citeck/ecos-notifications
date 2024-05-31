package ru.citeck.ecos.notifications.domain.file.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonProperty
import org.apache.commons.collections.CollectionUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.service.FileService
import ru.citeck.ecos.notifications.domain.template.getContentBytesFromBase64ObjectData
import ru.citeck.ecos.notifications.utils.LegacyRecordsUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordMeta
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.request.delete.RecordsDelResult
import ru.citeck.ecos.records2.request.delete.RecordsDeletion
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant
import java.util.stream.Collectors

const val ID = "file"

@Component
class FileRecords(val fileService: FileService) :
    LocalRecordsDao(),
    LocalRecordsQueryWithMetaDao<FileRecords.FileRecord>,
    LocalRecordsMetaDao<FileRecords.FileRecord>,
    MutableRecordsLocalDao<FileRecords.FileRecord> {

    init {
        id = ID
    }

    override fun queryLocalRecords(
        recordsQuery: RecordsQuery,
        metaField: MetaField
    ): RecordsQueryResult<FileRecord> {
        val records = RecordsQueryResult<FileRecord>()
        var max = recordsQuery.maxItems
        if (max <= 0) {
            max = 10000
        }

        val skip = recordsQuery.skipCount
        if (PredicateService.LANGUAGE_PREDICATE == recordsQuery.language) {
            val predicate = recordsQuery.getQuery(Predicate::class.java)
            val types: Collection<FileRecord> = fileService.getAll(
                max,
                recordsQuery.skipCount,
                predicate,
                LegacyRecordsUtils.mapLegacySortBy(recordsQuery.sortBy)
            )
                .stream()
                .map { dto: FileWithMeta -> FileRecord(dto) }
                .collect(Collectors.toList())
            records.records = ArrayList(types)
            records.totalCount = fileService.getCount(predicate)
            return records
        }
        if ("criteria" == recordsQuery.language) {
            records.records = fileService.getAll(max, skip)
                .stream()
                .map { dto: FileWithMeta -> FileRecord(dto) }
                .collect(Collectors.toList())
            records.totalCount = fileService.getCount()
            return records
        }
        return RecordsQueryResult()
    }

    override fun getLocalRecordsMeta(records: MutableList<EntityRef>, p1: MetaField): MutableList<FileRecord> {
        return records.stream()
            .map { obj: EntityRef -> obj.getLocalId() }
            .map { id: String? ->
                fileService.findById(id!!)
                    .orElseGet { FileWithMeta(id) }
            }
            .map { dto: FileWithMeta -> FileRecord(dto) }
            .collect(Collectors.toList())
    }

    override fun delete(deletion: RecordsDeletion): RecordsDelResult {
        val result = RecordsDelResult()
        for (record in deletion.records) {
            fileService.deleteById(record.getLocalId())
            result.addRecord(RecordMeta(record))
        }
        return result
    }

    override fun getValuesToMutate(records: MutableList<EntityRef>): MutableList<FileRecord> {
        return getLocalRecordsMeta(records, EmptyMetaField.INSTANCE)
    }

    override fun save(values: MutableList<FileRecord>): RecordsMutResult {
        val result = RecordsMutResult()
        val savedList = values.stream()
            .map { dto: FileRecord -> fileService.save(dto) }
            .map(FileWithMeta::id)
            .map { id: String? -> RecordMeta(id) }
            .collect(Collectors.toList())
        result.records = savedList
        return result
    }

    open inner class FileRecord(val dto: FileWithMeta) : FileWithMeta(dto) {
        var moduleId: String
            get() = id
            set(value) {
                id = value
            }

        @get:MetaAtt(".type")
        val ecosType: RecordRef
            get() = RecordRef.create("emodel", "type", "notification-file")

        @get:MetaAtt(".disp")
        val displayName: String?
            get() = id

        @get:MetaAtt(RecordConstants.ATT_MODIFIED)
        val recordModified: Instant?
            get() = modified

        @get:MetaAtt(RecordConstants.ATT_MODIFIER)
        val recordModifier: String?
            get() = modifier

        @get:MetaAtt(RecordConstants.ATT_CREATED)
        val recordCreated: Instant?
            get() = created

        @get:MetaAtt(RecordConstants.ATT_CREATOR)
        val recordCreator: String?
            get() = creator

        @JsonProperty("_content")
        fun fillFileContent(content: List<ObjectData>) {
            if (CollectionUtils.isEmpty(content)) {
                throw IllegalArgumentException("Content cannot be empty for notification file")
            }

            if (content.size > 1) {
                throw IllegalArgumentException("Only single content support")
            }

            val data = content[0]
            val fileName = data.get("originalName").asText()
            val contentBytes = getContentBytesFromBase64ObjectData(data)

            this.id = fileName
            this.data = contentBytes
        }
    }
}
