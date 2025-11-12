package ru.citeck.ecos.notifications.domain.file.api.records

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.collections4.CollectionUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.common.NotificationsSystemArtifactPerms
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.service.FileService
import ru.citeck.ecos.notifications.domain.template.getContentBytesFromBase64ObjectData
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import java.time.Instant
import java.util.stream.Collectors

@Component
class FileRecords(
    private val fileService: FileService,
    private val perms: NotificationsSystemArtifactPerms
) :
    AbstractRecordsDao(),
    RecordsQueryDao,
    RecordAttsDao,
    RecordsDeleteDao,
    RecordMutateDtoDao<FileRecords.FileRecord> {

    companion object {
        const val ID = "file"
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val records = RecsQueryRes<FileRecord>()
        var max = recsQuery.page.maxItems
        if (max <= 0) {
            max = 10000
        }

        val skip = recsQuery.page.skipCount
        if (PredicateService.LANGUAGE_PREDICATE == recsQuery.language) {
            val predicate = recsQuery.getQuery(Predicate::class.java)
            val types: Collection<FileRecord> = fileService.getAll(
                max,
                recsQuery.page.skipCount,
                predicate,
                recsQuery.sortBy
            ).stream()
                .map { dto: FileWithMeta -> FileRecord(dto) }
                .collect(Collectors.toList())
            records.setRecords(ArrayList(types))
            records.setTotalCount(fileService.getCount(predicate))
            return records
        }
        if ("criteria" == recsQuery.language) {
            records.setRecords(
                fileService.getAll(max, skip)
                    .stream()
                    .map { dto: FileWithMeta -> FileRecord(dto) }
                    .collect(Collectors.toList())
            )
            records.setTotalCount(fileService.getCount())
            return records
        }
        return RecsQueryRes<Any>()
    }

    override fun getRecordAtts(recordId: String): FileRecord {
        return FileRecord(
            fileService.findById(recordId)
                .orElseGet { FileWithMeta(recordId) }
        )
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        val result = ArrayList<DelStatus>()
        for (recordId in recordIds) {
            fileService.deleteById(recordId)
            result.add(DelStatus.OK)
        }
        return result
    }

    override fun getRecToMutate(recordId: String): FileRecord {
        return getRecordAtts(recordId)
    }

    override fun saveMutatedRec(record: FileRecord): String {
        return fileService.save(record).id
    }

    override fun getId(): String {
        return ID
    }

    open inner class FileRecord(val dto: FileWithMeta) : FileWithMeta(dto) {
        var moduleId: String
            get() = id
            set(value) {
                id = value
            }

        @get:AttName(".type")
        val ecosType: EntityRef
            get() = EntityRef.create("emodel", "type", "notification-file")

        @get:AttName(".disp")
        val displayName: String
            get() = id

        @get:AttName(RecordConstants.ATT_MODIFIED)
        val recordModified: Instant?
            get() = modified

        @get:AttName(RecordConstants.ATT_MODIFIER)
        val recordModifier: String?
            get() = modifier

        @get:AttName(RecordConstants.ATT_CREATED)
        val recordCreated: Instant?
            get() = created

        @get:AttName(RecordConstants.ATT_CREATOR)
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
            val fileName = data["originalName"].asText()
            val contentBytes = getContentBytesFromBase64ObjectData(data)

            this.id = fileName
            this.data = contentBytes
        }

        @get:AttName("permissions")
        val permissions: RecordPerms
            get() = perms.getPerms(EntityRef.create(AppName.NOTIFICATIONS, ID, dto.id))
    }
}
