package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.perms.BulkMailPerms
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailOperator
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import java.time.Instant

@Component
class BulkMailRecords(
    private val bulkMailDao: BulkMailDao,
    private val bulkMailOperator: BulkMailOperator,
    private val notificationDao: NotificationDao,
    private val perms: BulkMailPerms
) : AbstractRecordsDao(),
    RecordsQueryDao,
    RecordAttsDao,
    RecordMutateDtoDao<BulkMailRecords.BulkMailRecord>,
    RecordDeleteDao {

    companion object {
        const val ID = "bulk-mail"
    }

    override fun delete(recordId: String): DelStatus {
        val dto = bulkMailDao.findByExtId(recordId)
            ?: throw IllegalArgumentException("Bulk mail with ext id $recordId not found")

        bulkMailDao.remove(dto)

        return DelStatus.OK
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

                val types = bulkMailDao.getAll(
                    max,
                    recsQuery.page.skipCount,
                    predicate,
                    recsQuery.sortBy
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

    open inner class BulkMailRecord(
        var id: Long? = null,
        var name: String? = null,
        var extId: String? = null,
        val recipientsData: BulkMailRecipientsDataDto? = BulkMailRecipientsDataDto(),
        var record: EntityRef? = EntityRef.EMPTY,
        var template: EntityRef? = EntityRef.EMPTY,
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

        @get:AttName("stateSummary")
        val stateSummary: String
            get() = let {
                return notificationDao.getBulkMailStateSummary("notifications/$ID@$recordId").map {
                    "${it.key.name}: ${it.value}"
                }.joinToString("\n")
            }

        @get:AttName(".type")
        val ecosType: EntityRef
            get() = EntityRef.create("emodel", "type", "bulk-mail")

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

        @get:AttName("permissions")
        val permissions: RecordPerms
            get() = perms.getPerms(EntityRef.create(AppName.NOTIFICATIONS, ID, extId))
    }

    enum class BulkMailAction {
        NONE,
        CALCULATE_RECIPIENTS,
        DISPATCH
    }
}
