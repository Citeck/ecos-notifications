package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailRecipientDao
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

/**
 * @author Roman Makarskiy
 */
@Component
class BulkMailRecipientRecords(
    private val bulkMailRecipientDao: BulkMailRecipientDao
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao, RecordsDeleteDao {

    companion object {
        const val ID = "bulk-mail-recipient"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<BulkMailRecipientRecord> {
        val result = RecsQueryRes<BulkMailRecipientRecord>()

        when (recsQuery.language) {
            PredicateService.LANGUAGE_PREDICATE -> {

                val predicate = recsQuery.getQuery(Predicate::class.java)

                var max: Int = recsQuery.page.maxItems
                if (max <= 0) {
                    max = 100_000
                }

                val types = bulkMailRecipientDao.getAll(
                    max,
                    recsQuery.page.skipCount,
                    predicate,
                    recsQuery.sortBy
                )

                result.setRecords(types.map { BulkMailRecipientRecord(it) })
                result.setTotalCount(bulkMailRecipientDao.getCount(predicate))
            }
            else -> {
                val max: Int = recsQuery.page.maxItems
                val types = if (max < 0) {
                    bulkMailRecipientDao.getAll()
                } else {
                    bulkMailRecipientDao.getAll(max, recsQuery.page.skipCount)
                }
                result.setRecords(types.map { BulkMailRecipientRecord(it) })
            }
        }

        return result
    }

    override fun getRecordAtts(recordId: String): BulkMailRecipientRecord? {
        if (recordId.isBlank()) {
            return BulkMailRecipientRecord()
        }

        val dto = bulkMailRecipientDao.findByExtId(recordId)

        return dto?.let {
            BulkMailRecipientRecord(it)
        }
    }

    override fun delete(recordsId: List<String>): List<DelStatus> {
        val result = generateSequence { DelStatus.OK }.take(recordsId.size).toMutableList()

        bulkMailRecipientDao.removeAllByExtId(recordsId)

        return result
    }

    data class BulkMailRecipientRecord(
        var id: Long? = null,
        var extId: String? = null,
        val bulkMailRef: EntityRef = EntityRef.EMPTY,
        val record: EntityRef = EntityRef.EMPTY,
        val address: String = "",
        val name: String = "",
        val creator: String? = null,
        val created: Instant? = null,
        val modifier: String? = null,
        val modified: Instant? = null,
    ) {
        constructor(dto: BulkMailRecipientDto) : this(
            dto.id,
            dto.extId,
            dto.bulkMailRef,
            dto.record,
            dto.address,
            dto.name,
            dto.createdBy,
            dto.createdDate,
            dto.lastModifiedBy,
            dto.lastModifiedDate
        )

        var moduleId: String
            get() = let {
                return extId ?: ""
            }
            set(value) {
                extId = value
            }

        @get:AttName(".id")
        val recordId: String
            get() = moduleId

        @get:AttName(".disp")
        val disp: String
            get() = name

        @get:AttName(".type")
        val ecosType: EntityRef
            get() = EntityRef.create("emodel", "type", "bulk-mail-recipient")

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
