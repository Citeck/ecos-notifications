package ru.citeck.ecos.notifications.domain.template.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import java.time.Instant

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
open class NotificationTemplateDto(
    var id: String,
    var name: String? = null,
    var notificationTitle: MLText? = null,
    var data: Map<String, TemplateDataDto> = mapOf(),
    var model: ObjectData? = ObjectData.create(),
    var modifier: String? = null,
    var modified: Instant? = null,
    var creator: String? = null,
    var created: Instant? = null
) {
    constructor(dto: NotificationTemplateDto) : this(
        dto.id,
        dto.name,
        dto.notificationTitle,
        dto.data,
        dto.model,
        dto.modifier,
        dto.modified,
        dto.creator,
        dto.created
    )

    override fun toString(): String {
        return "NotificationTemplateDto(id='$id', " +
            "name=$name, " +
            "notificationTitle=$notificationTitle, " +
            "data=$data, " +
            "modifier=$modifier, " +
            "modified=$modified, " +
            "creator=$creator, " +
            "created=$created)"
    }

}
