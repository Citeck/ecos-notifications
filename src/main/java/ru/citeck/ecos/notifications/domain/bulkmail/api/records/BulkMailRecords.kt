package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailOperator
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
    private val bulkMailDao: BulkMailDao,
    private val bulkMailOperator: BulkMailOperator
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

        val dto = bulkMailDao.findByExtId(recordId)

        return dto?.let {
            BulkMailRecord(it)
        }
    }


    override fun getRecToMutate(recordId: String): BulkMailRecord {
        return getRecordAtts(recordId) ?: BulkMailRecord()
    }

    override fun saveMutatedRec(record: BulkMailRecord): String {
        if (isActionMutate(record)) {
            processAction(record)
            return record.extId!!
        }

        return bulkMailDao.save(record.toDto()).extId!!
    }

    private fun processAction(record: BulkMailRecord) {
        when (record.action) {
            BulkMailAction.CALCULATE_RECIPIENTS -> bulkMailOperator.calculateRecipients(record.extId!!)
            BulkMailAction.DISPATCH -> bulkMailOperator.dispatch(record.extId!!)
            BulkMailAction.NONE -> {
            }
        }
    }

    private fun isActionMutate(record: BulkMailRecord): Boolean {
        return record.action != BulkMailAction.NONE
    }

    data class BulkMailRecord(
        var id: Long? = null,
        var name: String? = null,
        var extId: String? = null,
        val recipientsData: BulkMailRecipientsDataDto? = BulkMailRecipientsDataDto(),
        var record: RecordRef? = RecordRef.EMPTY,
        var template: RecordRef? = RecordRef.EMPTY,
        val type: NotificationType? = null,
        val title: String? = null,
        val body: String? = null,
        val config: BulkMailConfigDto? = BulkMailConfigDto(),
        val action: BulkMailAction = BulkMailAction.NONE,
        val status: String? = null,
        val creator: String? = null,
        val created: Instant? = null,
        val modifier: String? = null,
        val modified: Instant? = null
    ) {
        constructor(dto: BulkMailDto) : this(
            dto.id,
            dto.name,
            dto.extId,
            dto.recipientsData,
            dto.record,
            dto.template,
            dto.type,
            dto.title,
            dto.body,
            dto.config,
            BulkMailAction.NONE,
            dto.status,
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
        val disp: String?
            get() = name

        @get:AttName(StatusConstants.ATT_STATUS_STR)
        val statusStr: String?
            get() = status

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

    enum class BulkMailAction {
        NONE, CALCULATE_RECIPIENTS, DISPATCH
    }

}
