package ru.citeck.ecos.notifications.domain.sender.api.records

import com.fasterxml.jackson.annotation.JsonValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils.toNonDefaultString
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.license.EcosLicense
import ru.citeck.ecos.notifications.common.NotificationsSystemArtifactPerms
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.senders.EmailNotificationSenderConfig
import ru.citeck.ecos.notifications.service.senders.NotificationSenderType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
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
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
class NotificationsSenderRecordsDao(
    private val notificationsSenderService: NotificationsSenderService,
    private val perms: NotificationsSystemArtifactPerms
) : AbstractRecordsDao(),
    RecordsDeleteDao,
    RecordAttsDao,
    RecordMutateDtoDao<NotificationsSenderRecordsDao.NotificationsSenderRecord>,
    RecordsQueryDao {

    companion object {
        const val ID = "notifications-sender"
        private val log = KotlinLogging.logger {}
    }

    private val emailSignIsAllowed = EcosLicense.getForEntLib {
        it.get("developer").asBoolean() || it.get("features").has("email-certificate-sign")
    }

    override fun getId(): String {
        return ID
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        notificationsSenderService.removeAllByExtId(recordIds)
        return generateSequence { DelStatus.OK }.take(recordIds.size).toList()
    }

    override fun getRecordAtts(recordId: String): NotificationsSenderRecord? {
        if (recordId.isBlank()) {
            return NotificationsSenderRecord()
        }
        val dto = notificationsSenderService.getSenderById(recordId)

        return dto?.let {
            NotificationsSenderRecord(it)
        }
    }

    override fun getRecToMutate(recordId: String): NotificationsSenderRecord {
        return getRecordAtts(recordId) ?: NotificationsSenderRecord()
    }

    override fun saveMutatedRec(record: NotificationsSenderRecord): String {
        record.getEmailSenderConfig()?.validateForFastFail()

        return notificationsSenderService.save(record.toDto()).id!!
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<NotificationsSenderRecord> {
        val result = RecsQueryRes<NotificationsSenderRecord>()
        val sort = recsQuery.sortBy
        val (maxItems, skipCount) = recsQuery.page
        val maxItemsCount = if (maxItems <= 0) 10000 else maxItems

        when (recsQuery.language) {
            PredicateService.LANGUAGE_PREDICATE -> {
                val predicate = recsQuery.getQuery(Predicate::class.java)
                result.setRecords(
                    notificationsSenderService.getAll(maxItemsCount, skipCount, predicate, sort)
                        .map { NotificationsSenderRecord(it) }
                        .toList()
                )
                result.setTotalCount(notificationsSenderService.getCount(predicate))
            }

            else -> {
                log.warn("Unsupported query language '{}'", recsQuery.language)
                val types = if (maxItems < 0) {
                    notificationsSenderService.getAll()
                } else {
                    notificationsSenderService.getAll(maxItems, skipCount, VoidPredicate.INSTANCE, emptyList())
                }
                result.setRecords(types.map { NotificationsSenderRecord(it) }.toList())
                result.setTotalCount(notificationsSenderService.getCount())
            }
        }
        return result
    }

    private fun NotificationsSenderRecord.getEmailSenderConfig(): EmailNotificationSenderConfig? {
        if (NotificationSenderType.fromType(senderType) == NotificationSenderType.DEFAULT &&
            notificationType == NotificationType.EMAIL_NOTIFICATION
        ) {
            return senderConfig.getAs(EmailNotificationSenderConfig::class.java)
        }

        return null
    }

    private fun EmailNotificationSenderConfig.validateForFastFail() {
        if (certSignConfig.enabled) {
            check(emailSignIsAllowed()) {
                "Подписание e-mail сертификатом доступно только в Enterprise версии, с функцией email-certificate-sign"
            }

            require(certSignConfig.certificate.isNotEmpty()) {
                "Не указан сертификат для подписи e-mail"
            }
        }
    }

    inner class NotificationsSenderRecord(
        var id: String? = null,
        var name: String? = null,
        var enabled: Boolean = false,
        var condition: Predicate? = null,
        var notificationType: NotificationType? = null,
        var order: Float? = null,
        var senderType: String? = null,
        var templates: List<EntityRef> = emptyList(),
        var senderConfig: ObjectData = ObjectData.create(),

        var creator: String? = null,
        var created: Instant? = null,
        var modifier: String? = null,
        var modified: Instant? = null
    ) {

        constructor(dtoWithMeta: NotificationsSenderDtoWithMeta) : this(
            dtoWithMeta.id,
            dtoWithMeta.name,
            dtoWithMeta.enabled,
            dtoWithMeta.condition,
            dtoWithMeta.notificationType,
            dtoWithMeta.order,
            dtoWithMeta.senderType,
            dtoWithMeta.templates,
            dtoWithMeta.senderConfig,
            dtoWithMeta.creator,
            dtoWithMeta.created,
            dtoWithMeta.modifier,
            dtoWithMeta.modified
        )

        @JsonValue
        fun toNonDefaultJson(): Any {
            return mapper.toNonDefaultJson(this.toDto())
        }

        val data: ByteArray
            get() = toNonDefaultString(toNonDefaultJson()).toByteArray(StandardCharsets.UTF_8)

        var moduleId: String
            get() = let {
                return id ?: ""
            }
            set(value) {
                id = value
            }

        @get:AttName(".id")
        val recordId: String
            get() = moduleId

        @get:AttName(".type")
        val ecosType: EntityRef
            get() = EntityRef.create(AppName.EMODEL, "type", "notifications-sender")

        fun getRef(): EntityRef {
            return EntityRef.create(AppName.NOTIFICATIONS, ID, id)
        }

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

        @get:AttName(RecordConstants.ATT_DISP)
        val disp: String
            get() = let {
                val dispName = MLText(
                    I18nContext.ENGLISH to "Sender $id $senderType",
                    I18nContext.RUSSIAN to "Отправитель $id $senderType"
                )
                return dispName.get(I18nContext.getLocale())
            }

        @get:AttName("permissions")
        val permissions: RecordPerms
            get() = perms.getPerms(EntityRef.create(AppName.NOTIFICATIONS, ID, id))
    }
}
