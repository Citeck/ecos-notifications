package ru.citeck.ecos.notifications.domain.sender.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonValue
import ecos.com.fasterxml.jackson210.databind.JsonNode
import lombok.Data
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils.toNonDefaultString
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicate
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
    fun toNonDefaultJson(): JsonNode {
        return mapper.toNonDefaultJson(this.toDto())
    }

    val data: ByteArray
        get() = toNonDefaultString(toNonDefaultJson()).toByteArray(StandardCharsets.UTF_8)
    val ecosType: String
        get() = "notifications-sender"
}
