package ru.citeck.ecos.notifications.domain.template.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import java.time.Instant

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
open class NotificationTemplateWithMeta(
    id: String,
    name: String? = null,
    notificationTitle: MLText? = null,
    model: ObjectData? = ObjectData.create(),
    var templateData: Map<String, TemplateDataDto> = mapOf(),
    var modifier: String? = null,
    var modified: Instant? = null,
    var creator: String? = null,
    var created: Instant? = null
) : NotificationTemplateDto(id, name, notificationTitle, model) {
    constructor(dto: NotificationTemplateWithMeta) : this(
        dto.id,
        dto.name,
        dto.notificationTitle,
        dto.model,
        dto.templateData,
        dto.modifier,
        dto.modified,
        dto.creator,
        dto.created
    )
}
