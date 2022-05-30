package ru.citeck.ecos.notifications.domain.sender.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonValue
import lombok.Data
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
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Class supports uploading board from yml-file,
 * editing sender with JSON Editor
 */
@Data
class NotificationsSenderRecord (
    var id: String?= null,
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
    ){

    constructor(dtoWithMeta: NotificationsSenderDtoWithMeta): this (
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
    open fun toNonDefaultJson(): Any {
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

    open fun getRef(): RecordRef {
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
}
