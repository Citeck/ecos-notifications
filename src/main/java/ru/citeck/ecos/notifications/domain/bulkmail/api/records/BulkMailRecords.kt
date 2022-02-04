package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import java.time.Instant

@Component
class BulkMailRecords(
    private val bulkMailDao: BulkMailDao
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao, RecordMutateDtoDao<BulkMailRecords.BulkMailRecord> {

    companion object {
        const val ID = "bulk-mail"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<BulkMailRecord> {
        val result = RecsQueryRes<BulkMailRecord>()

        when (recsQuery.language) {
            PredicateService.LANGUAGE_PREDICATE -> {

                val predicate = recsQuery.getQuery(Predicate::class.java)

                var max: Int = recsQuery.page.maxItems
                if (max <= 0) {
                    max = 100_000
                }
                val order: List<Sort.Order> = recsQuery.sortBy
                    .mapNotNull { sortBy ->
                        var attribute = sortBy.attribute
                        attribute = if (RecordConstants.ATT_MODIFIED == attribute) {
                            "lastModifiedDate"
                        } else {
                            ""
                        }
                        if (attribute.isNotBlank()) {
                            if (sortBy.ascending) {
                                Sort.Order.asc(attribute)
                            } else {
                                Sort.Order.desc(attribute)
                            }
                        } else {
                            null
                        }
                    }

                val types = bulkMailDao.getAll(
                    max,
                    recsQuery.page.skipCount,
                    predicate,
                    if (order.isNotEmpty()) {
                        Sort.by(order)
                    } else {
                        null
                    }
                )

                result.setRecords(types.map { BulkMailRecord(it) })
                result.setTotalCount(bulkMailDao.getCount(predicate))
            }
            else -> {
                val max: Int = recsQuery.page.maxItems
                val types = if (max < 0) {
                    bulkMailDao.getAll()
                } else {
                    bulkMailDao.getAll(max, recsQuery.page.skipCount)
                }
                result.setRecords(types.map { BulkMailRecord(it) })
            }
        }

        return result
    }

    override fun getRecordAtts(recordId: String): BulkMailRecord? {
        if (recordId.isBlank()) {
            return BulkMailRecord()
        }

        val dto = bulkMailDao.findByExtId(recordId) ?: recordId.toLongOrNull()?.let { longId ->
            bulkMailDao.findById(longId)
        }

        return dto?.let {
            BulkMailRecord(it)
        }
    }


    override fun getRecToMutate(recordId: String): BulkMailRecord {
        return getRecordAtts(recordId) ?: BulkMailRecord()
    }

    override fun saveMutatedRec(record: BulkMailRecord): String {
        return bulkMailDao.save(record.toDto()).extId!!
    }

    open inner class BulkMailRecord(
        var id: Long? = null,
        var extId: String? = null,
        val recipientsData: ObjectData? = ObjectData.create(),
        var record: RecordRef? = RecordRef.EMPTY,
        var template: RecordRef? = RecordRef.EMPTY,
        val type: NotificationType? = null,
        val title: String? = null,
        val body: String? = null,
        val config: ObjectData? = ObjectData.create(),
        val status: String? = null,
        val creator: String? = null,
        val created: Instant? = null,
        val modifier: String? = null,
        val modified: Instant? = null,
    ) {
        constructor(dto: BulkMailDto) : this(
            dto.id,
            dto.extId,
            ObjectData.create(dto.recipientsData),
            dto.record,
            dto.template,
            dto.type,
            dto.title,
            dto.body,
            ObjectData.create(dto.config),
            dto.status,
            dto.createdBy,
            dto.createdDate,
            dto.lastModifiedBy,
            dto.lastModifiedDate
        )

        var moduleId: String
            get() = let {
                return extId?.ifBlank {
                    id.toString()
                } ?: ""
            }
            set(value) {
                extId = value
            }

        @get:AttName(".id")
        val recordId: String
            get() = moduleId

        @get:AttName(".disp")
        val disp: String
            get() = moduleId

        @get:AttName(".type")
        val ecosType: RecordRef
            get() = RecordRef.create("emodel", "type", "bulk-mail")

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
    }

}
