package ru.citeck.ecos.notifications.domain.template.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonProperty
import org.apache.commons.lang.LocaleUtils
import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.template.api.records.NotificationTemplateRecords.NotTemplateRecord
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.getContentBytesFromBase64ObjectData
import ru.citeck.ecos.notifications.domain.template.getLangKeyFromFileName
import ru.citeck.ecos.notifications.domain.template.hasLangKey
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordMeta
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
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
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.time.Instant
import java.util.stream.Collectors

private const val META_FILE_EXTENSION = "meta.yml"

private val TEMPLATE_INFO_MAP = mapOf(
    "model" to "model?json",
    "multiTemplateConfig" to "multiTemplateConfig[]?json"
)

const val ID = "template"

@Component
class NotificationTemplateRecords(val templateService: NotificationTemplateService) : LocalRecordsDao(),
    LocalRecordsQueryWithMetaDao<NotTemplateRecord>,
    LocalRecordsMetaDao<NotTemplateRecord>,
    MutableRecordsLocalDao<NotTemplateRecord> {

    @Autowired
    private lateinit var sendersService: NotificationSenderService

    init {
        id = ID
    }

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
            .map { dto: NotTemplateRecord -> templateService.save(dto) }
            .map(NotificationTemplateWithMeta::id)
            .map { id: String? -> RecordMeta(id) }
            .collect(Collectors.toList())
        result.records = savedList
        return result
    }

    override fun getLocalRecordsMeta(records: List<RecordRef>, metaField: MetaField): List<NotTemplateRecord> {
        return records.stream()
            .map { obj: RecordRef -> obj.id }
            .map { id: String ->
                templateService.findById(id)
                    .orElseGet { NotificationTemplateWithMeta(id) }
            }
            .map { dto: NotificationTemplateWithMeta -> NotTemplateRecord(dto) }
            .collect(Collectors.toList())
    }

    override fun queryLocalRecords(
        recordsQuery: RecordsQuery,
        metaField: MetaField
    ): RecordsQueryResult<NotTemplateRecord> {
        val records = RecordsQueryResult<NotTemplateRecord>()
        var max = recordsQuery.maxItems
        if (max <= 0) {
            max = 10000
        }

        val skip = recordsQuery.skipCount
        if (PredicateService.LANGUAGE_PREDICATE == recordsQuery.language) {
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
                .map { dto: NotificationTemplateWithMeta -> NotTemplateRecord(dto) }
                .collect(Collectors.toList())
            records.records = ArrayList(types)
            records.totalCount = templateService.getCount(predicate)
            return records
        }
        if ("criteria" == recordsQuery.language) {
            records.records = templateService.getAll(max, skip)
                .stream()
                .map { dto: NotificationTemplateWithMeta -> NotTemplateRecord(dto) }
                .collect(Collectors.toList())
            records.totalCount = templateService.count
            return records
        }
        return RecordsQueryResult()
    }

    open inner class NotTemplateRecord(val dto: NotificationTemplateWithMeta) : NotificationTemplateWithMeta(dto) {
        var moduleId: String
            get() = id
            set(value) {
                id = value
            }

        @get:AttName(".type")
        val ecosType: RecordRef
            get() = RecordRef.create("emodel", "type", "notification-template")

        @get:AttName(".disp")
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
            get() = let {
                val attributes = mutableSetOf<String>()

                this.model?.forEach { (_, dataValue) -> attributes.add(dataValue) }

                addAttributesRecursive(this.multiTemplateConfig, attributes)

                attributes.addAll(sendersService.getModel())

                return attributes;
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
                    if (it.id.isNotEmpty()) {
                        val data = recordsService.getAttributes(it, TEMPLATE_INFO_MAP)

                        data.get("model").asMap(String::class.java, String::class.java).forEach { (_, attr) ->
                            attributes.add(attr)
                        }

                        val newMultiTemplateConfig = data.get("multiTemplateConfig")
                            .asList(MultiTemplateElementDto::class.java)
                        addAttributesRecursive(newMultiTemplateConfig, attributes)
                    }
                }
            }
        }

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

                    val fileName = "${moduleId}.html_${locale}.ftl"
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
                val prettyString = mapper.toPrettyString(metaDto)

                var hasLangKeyInTemplateData = false

                templateData.forEach { (_, data) ->
                    if (hasLangKey(data.name)) {
                        hasLangKeyInTemplateData = true
                    }
                    memDir.createFile(data.name, data.data)
                }

                mapper.toBytes(prettyString)?.let {
                    val name = if (hasLangKeyInTemplateData) "$id.html.$META_FILE_EXTENSION"
                    else "$id.html.ftl.$META_FILE_EXTENSION"

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
    }

    data class BodyTemplateData(
        var lang: String = "",
        var body: String = ""
    )

}
