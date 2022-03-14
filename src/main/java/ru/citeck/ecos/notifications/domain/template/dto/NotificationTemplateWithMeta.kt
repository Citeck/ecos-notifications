package ru.citeck.ecos.notifications.domain.template.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude
import ru.citeck.ecos.commons.data.MLText
import java.time.Instant

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
open class NotificationTemplateWithMeta(
    id: String,
    name: String? = null,
    notificationTitle: MLText? = null,
    tags: List<String> = emptyList(),
    model: Map<String, String>? = emptyMap(),
    multiTemplateConfig: List<MultiTemplateElementDto>? = emptyList(),
    var templateData: Map<String, TemplateDataDto> = mapOf(),
    var modifier: String? = null,
    var modified: Instant? = null,
    var creator: String? = null,
    var created: Instant? = null
) : NotificationTemplateDto(id, name, notificationTitle, tags, model, multiTemplateConfig) {
    constructor(dto: NotificationTemplateWithMeta) : this(
        dto.id,
        dto.name,
        dto.notificationTitle,
        dto.tags,
        dto.model,
        dto.multiTemplateConfig,
        dto.templateData,
        dto.modifier,
        dto.modified,
        dto.creator,
        dto.created
    )

    override fun toString(): String {
        return "NotificationTemplateWithMeta(id=$id)"
    }

}
