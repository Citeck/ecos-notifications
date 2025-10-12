package ru.citeck.ecos.notifications.domain.template.api.records

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.notifications.domain.template.api.records.NotificationTemplateRecords.NotTemplateRecord
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.getContentBytesFromBase64ObjectData
import ru.citeck.ecos.notifications.domain.template.getLangKeyFromFileName
import ru.citeck.ecos.notifications.domain.template.hasLangKey
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateAttsCalculator
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
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
import java.time.Instant
import java.util.stream.Collectors

private const val META_FILE_EXTENSION = "meta.yml"

const val NOTIFICATION_TEMPLATE_RECORD_ID = "template"

@Component
class NotificationTemplateRecords(
    val templateService: NotificationTemplateService,
    val notificationTemplateAttsCalculator: NotificationTemplateAttsCalculator,
    val workspaceService: WorkspaceService
) :
    AbstractRecordsDao(),
    RecordsQueryDao,
    RecordAttsDao,
    RecordsDeleteDao,
    RecordMutateDtoDao<NotTemplateRecord> {

    override fun delete(recordIds: List<String>): List<DelStatus> {
        val result = ArrayList<DelStatus>()
        for (recordId in recordIds) {
            templateService.deleteById(workspaceService.convertToIdInWs(recordId))
            result.add(DelStatus.OK)
        }
        return result
    }

    override fun getRecToMutate(recordId: String): NotTemplateRecord {
        if (recordId.isEmpty()) {
            return NotTemplateRecord(NotificationTemplateWithMeta("", ""))
        }
        return getRecordAtts(recordId) ?: error("Record with id '$recordId' is not found")
    }

    override fun saveMutatedRec(record: NotTemplateRecord): String {
        val saved = templateService.save(record)
        return workspaceService.addWsPrefixToId(saved.id, saved.workspace)
    }

    override fun getRecordAtts(recordId: String): NotTemplateRecord? {
        val idInWs = workspaceService.convertToIdInWs(recordId)
        return templateService.findById(idInWs).orElse(null)?.let {
            NotTemplateRecord(it)
        }
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val records = RecsQueryRes<NotTemplateRecord>()
        var max = recsQuery.page.maxItems
        if (max <= 0) {
            max = 10000
        }

        val skip = recsQuery.page.skipCount
        if (PredicateService.LANGUAGE_PREDICATE == recsQuery.language) {
            val predicate = recsQuery.getQuery(Predicate::class.java)
            val types: Collection<NotTemplateRecord> = templateService.getAll(
                max,
                recsQuery.page.skipCount,
                predicate,
                recsQuery.workspaces,
                recsQuery.sortBy
            ).map { dto: NotificationTemplateWithMeta -> NotTemplateRecord(dto) }
            records.setRecords(ArrayList(types))
            records.setTotalCount(templateService.getCount(predicate))
            return records
        }
        if ("criteria" == recsQuery.language) {
            records.setRecords(
                templateService.getAll(max, skip)
                    .stream()
                    .map { dto: NotificationTemplateWithMeta -> NotTemplateRecord(dto) }
                    .collect(Collectors.toList())
            )
            records.setTotalCount(templateService.count)
            return records
        }
        return RecsQueryRes<Any>()
    }

    override fun getId(): String {
        return NOTIFICATION_TEMPLATE_RECORD_ID
    }

    open inner class NotTemplateRecord(val dto: NotificationTemplateWithMeta) : NotificationTemplateWithMeta(dto) {
        var moduleId: String
            get() = id
            set(value) {
                id = value
            }

        @AttName(ScalarType.ID_SCHEMA)
        fun getRef(): EntityRef {
            val strId = workspaceService.addWsPrefixToId(id, workspace)
            return EntityRef.create(AppName.NOTIFICATIONS, NOTIFICATION_TEMPLATE_RECORD_ID, strId)
        }

        @get:AttName("?type")
        val ecosType: EntityRef
            get() = EntityRef.create("emodel", "type", "notification-template")

        @get:AttName("?disp")
        val displayName: String?
            get() = name

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

        @get:AttName("multiModelAttributes")
        val multiModel: Set<String>
            get() = notificationTemplateAttsCalculator.getAllRequiredAtts(this)

        @get:AttName("title")
        val title: MLText
            get() = dto.notificationTitle ?: MLText.EMPTY

        @get:AttName("body")
        val body: MLText
            get() = let {
                var body = MLText()

                for ((locale, data) in dto.templateData) {
                    body = body.withValue(LocaleUtils.toLocale(locale), String(data.data))
                }

                return body
            }

        @get:AttName("bodyData")
        var bodyData: List<BodyTemplateData>
            get() = let {
                val result = mutableListOf<BodyTemplateData>()

                for ((locale, data) in body.getValues()) {
                    result.add(BodyTemplateData(locale.toString(), data))
                }

                return result
            }
            set(value) {

                val newTemplateData: MutableMap<String, TemplateDataDto> = mutableMapOf()

                value.forEach {
                    val locale = LocaleUtils.toLocale(it.lang)

                    val fileName = "$moduleId.html_$locale.ftl"
                    val templateData = TemplateDataDto(fileName, it.body.toByteArray(Charsets.UTF_8))

                    newTemplateData[locale.toString()] = templateData
                }

                this.templateData = newTemplateData
            }

        @get:AttName("data")
        val data: ByteArray
            get() = let {
                val memDir = EcosMemDir()

                val metaDto = NotificationTemplateDto(this)
                metaDto.workspace = ""
                val prettyString = mapper.toPrettyString(metaDto)

                var hasLangKeyInTemplateData = false

                templateData.forEach { (_, data) ->
                    if (hasLangKey(data.name)) {
                        hasLangKeyInTemplateData = true
                    }
                    memDir.createFile(data.name, data.data)
                }

                mapper.toBytes(prettyString)?.let {
                    val name = if (hasLangKeyInTemplateData) {
                        "$id.html.$META_FILE_EXTENSION"
                    } else {
                        "$id.html.ftl.$META_FILE_EXTENSION"
                    }

                    memDir.createFile(name, it)
                }

                return ZipUtils.writeZipAsBytes(memDir)
            }

        @JsonProperty("_content")
        fun fillNotificationTemplateFromZip(content: List<ObjectData>) {
            val contentBytes = getContentBytesFromBase64ObjectData(content[0])

            val memDir = ZipUtils.extractZip(contentBytes)
            if (memDir.findFiles().isEmpty()) {
                throw IllegalStateException("Failed load notification template, zip is empty")
            }

            val templateData: MutableMap<String, TemplateDataDto> = mutableMapOf()
            var metaFile: EcosFile? = null

            memDir.findFiles().forEach {
                if (StringUtils.endsWith(it.getName(), ".$META_FILE_EXTENSION")) {
                    metaFile = it
                } else {
                    val fileName = it.getName()
                    val langKey = getLangKeyFromFileName(fileName)
                    templateData[langKey] = TemplateDataDto(fileName, it.readAsBytes())
                }
            }

            val metaBytes = metaFile?.readAsBytes()
                ?: throw IllegalStateException("Failed load notification template, json with meta not found")

            val data = mapper.read(metaBytes, ObjectData::class.java)

            mapper.applyData(this, data)
            this.templateData = templateData
        }

        @JsonProperty(RecordConstants.ATT_WORKSPACE)
        fun setCtxWorkspace(workspace: String) {
            this.workspace = workspaceService.getUpdatedWsInMutation(this.workspace, workspace)
        }
    }

    data class BodyTemplateData(
        var lang: String = "",
        var body: String = ""
    )
}
