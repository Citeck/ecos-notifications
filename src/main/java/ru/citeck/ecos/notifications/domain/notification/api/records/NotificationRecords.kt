package ru.citeck.ecos.notifications.domain.notification.api.records

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.QueryContext
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import java.time.Instant

@Component
class NotificationRecords(
    private val notificationDao: NotificationDao
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {
        const val ID = "notification"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<NotificationRecord> {

        val result = RecsQueryRes<NotificationRecord>()

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

                val types = notificationDao.getAll(
                    max,
                    recsQuery.page.skipCount,
                    predicate,
                    if (order.isNotEmpty()) {
                        Sort.by(order)
                    } else {
                        null
                    }
                )

                result.setRecords(types.map { NotificationRecord(it) })
                result.setTotalCount(notificationDao.getCount(predicate))
            }
            else -> {
                val max: Int = recsQuery.page.maxItems
                val types = if (max < 0) {
                    notificationDao.getAll()
                } else {
                    notificationDao.getAll(max, recsQuery.page.skipCount)
                }
                result.setRecords(types.map { NotificationRecord(it) })
            }
        }

        return result
    }

    override fun getRecordAtts(recordId: String): NotificationRecord? {
        if (recordId.isBlank()) {
            return null
        }

        val dto = notificationDao.getByExtId(recordId) ?: recordId.toLongOrNull()?.let { longId ->
            notificationDao.getById(longId)
        }

        return dto?.let { NotificationRecord(it) }
    }

    open inner class NotificationRecord(
        var id: Long,
        var extId: String,
        val record: RecordRef,
        val template: RecordRef,
        val type: NotificationType? = null,
        val data: ByteArray?,
        val errorMessage: String,
        val errorStackTrace: String,
        val tryingCount: Int,
        val lastTryingDate: Instant?,
        val state: NotificationState,
        val creator: String?,
        val created: Instant?,
        val modifier: String?,
        val modified: Instant?
    ) {
        constructor(dto: NotificationDto) : this(
            dto.id,
            dto.extId,
            dto.record,
            dto.template,
            dto.type,
            dto.data,
            dto.errorMessage,
            dto.errorStackTrace,
            dto.tryingCount,
            dto.lastTryingDate,
            dto.state,
            dto.createdBy,
            dto.createdDate,
            dto.lastModifiedBy,
            dto.lastModifiedDate
        )

        var moduleId: String
            get() = let {
                return extId.ifBlank {
                    id.toString()
                }
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

        @get:AttName("notificationCommandPayload")
        val payload: String
            get() = let {
                data?.let { bytes ->
                    return Json.mapper.toPrettyString(String(bytes)) ?: ""
                }

                return ""
            }

        @get:AttName(".type")
        val ecosType: RecordRef
            get() = RecordRef.create("emodel", "type", "notification")

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
