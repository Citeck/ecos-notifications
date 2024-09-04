package ru.citeck.ecos.notifications.domain.notification.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.NotificationTemplateConverter
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant
import java.util.*

@Component
class NotificationRecords(
    private val notificationDao: NotificationDao,
    private val commandsService: CommandsService,
    private val notificationTemplateService: NotificationTemplateService,
    private val notificationTemplateConverter: NotificationTemplateConverter
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao, RecordMutateDao {

    companion object {
        const val ID = "notification"
        private const val APP_NAME = "notifications"
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

                val types = notificationDao.getAll(
                    max,
                    recsQuery.page.skipCount,
                    predicate,
                    recsQuery.sortBy
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

    override fun mutate(record: LocalRecordAtts): String {
        checkMutatePermissions()
        val action = record.attributes.get("action", "")
        if (action == "RESEND") {
            executeResendAction(record.id)
        } else {
            error("Unknown action: $action")
        }
        return record.id
    }

    private fun checkMutatePermissions() {
        if (!AuthContext.getCurrentAuthorities().contains(AuthRole.ADMIN) && !AuthContext.isRunAsSystem()) {
            error("Permission denied")
        }
    }

    private fun executeResendAction(recordId: String) {
        val dto = notificationDao.getByExtId(recordId)
            ?: error("Notification record is not found: $recordId")
        val data = dto.data?.decodeToString() ?: error("Can't unmarshall notification data")
        val notificationCommand = mapper.read(data, SendNotificationCommand::class.java)
            ?: error("Can't unmarshall notification data to SendNotificationCommand: $data")
        val newNotificationCommand = notificationCommand.copy(
            id = UUID.randomUUID().toString(),
            createdFrom = RecordRef.create(APP_NAME, ID, notificationCommand.id)
        )
        commandsService.executeSync(newNotificationCommand)
    }

    open inner class NotificationRecord(
        var id: Long? = null,
        var extId: String,
        val record: EntityRef,
        val template: EntityRef,
        val bulkMailRef: EntityRef = EntityRef.EMPTY,
        val type: NotificationType? = null,
        val data: ByteArray?,
        val errorMessage: String,
        val errorStackTrace: String,
        val tryingCount: Int,
        val lastTryingDate: Instant?,
        val delayedSend: Instant?,
        val createdFrom: EntityRef = EntityRef.EMPTY,
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
            dto.bulkMailRef,
            dto.type,
            dto.data,
            dto.errorMessage,
            dto.errorStackTrace,
            dto.tryingCount,
            dto.lastTryingDate,
            dto.delayedSend,
            dto.createdFrom,
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
                    return mapper.toPrettyString(String(bytes)) ?: ""
                }

                return ""
            }

        @get:AttName("sentNotification")
        val sentNotification: String
            get() = id?.let { notificationTemplateConverter.convertToReadableNotification(it, template) } ?: ""

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

        @get:AttName("model")
        val model: Map<String, Any>
            get() = let {

                val baseTemplate = notificationTemplateService.findById(template.getLocalId()).orElseThrow {
                    NotificationException("Template with id: <$id> not found}")
                }
                val notificationCommand = mapper.read(data, SendNotificationCommand::class.java)
                    ?: error("Can't unmarshall notification data to SendNotificationCommand: $data")

                val baseModel = baseTemplate.model
                val notificationCommandModel = notificationCommand.model

                if (baseModel.isNullOrEmpty() || notificationCommandModel.isNullOrEmpty()) {
                    return emptyMap()
                }

                val filledModel = mutableMapOf<String, Any>()

                baseModel.forEach { (attrKey, attrValue) ->
                    notificationCommandModel[attrValue]?.let {
                        filledModel[attrKey] = it
                    }
                }

                return filledModel
            }
    }
}
