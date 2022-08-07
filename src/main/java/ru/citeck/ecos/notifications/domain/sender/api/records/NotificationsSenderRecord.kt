package ru.citeck.ecos.notifications.domain.sender.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils.toNonDefaultString
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.request.RequestContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

/**
 * Class supports uploading board from yml-file,
 * editing sender with JSON Editor
 */
class NotificationsSenderRecord(
    var id: String? = null,
    var name: String? = null,
    var enabled: Boolean = false,
    var condition: Predicate? = null,
    var notificationType: NotificationType? = null,
    var order: Float? = null,
    var senderType: String? = null,
    var templates: List<RecordRef> = emptyList(),
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
    @com.fasterxml.jackson.annotation.JsonValue
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
    val ecosType: RecordRef
        get() = RecordRef.create("emodel", "type", "notifications-sender")

    fun getRef(): RecordRef {
        return RecordRef.create("notifications", NotificationsSenderRecordsDao.ID, id)
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
                Pair(Locale.ENGLISH, "Sender $id $senderType"),
                Pair(
                    Locale("ru"),
                    "\u041e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u0435\u043b\u044c $id $senderType"
                )
            )
            return dispName.get(RequestContext.getLocale())
        }
}
