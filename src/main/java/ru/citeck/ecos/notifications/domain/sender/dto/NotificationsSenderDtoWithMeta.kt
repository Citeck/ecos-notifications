package ru.citeck.ecos.notifications.domain.sender.dto

import lombok.Data
import lombok.EqualsAndHashCode
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.time.Instant

@Data
data class NotificationsSenderDtoWithMeta(
    var id: String? = null,
    var name: String? = null,
    var enabled: Boolean = false,
    var condition: Predicate? = null,
    var notificationType: NotificationType? = null,
    var order: Float? = null,
    var senderType: String? = null,
    var templates: List<RecordRef> = emptyList(),
    var senderConfig: ObjectData = ObjectData.create(),

    val creator: String? = null,
    val created: Instant? = null,
    val modifier: String? = null,
    val modified: Instant? = null
) {

    constructor(dto: NotificationsSenderDto) : this(
        dto.id,
        dto.name,
        dto.enabled,
        dto.condition,
        dto.notificationType,
        dto.order,
        dto.senderType,
        dto.templates,
        dto.senderConfig,
        dto.createdBy,
        dto.createdDate,
        dto.lastModifiedBy,
        dto.lastModifiedDate
    )

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
