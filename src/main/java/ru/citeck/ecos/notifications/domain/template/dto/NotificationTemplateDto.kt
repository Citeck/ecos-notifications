package ru.citeck.ecos.notifications.domain.template.dto

import ru.citeck.ecos.commons.data.MLText

open class NotificationTemplateDto(

    var id: String,
    var name: String? = null,
    var notificationTitle: MLText? = null,
    var model: Map<String, String>? = emptyMap(),
    var multiTemplateConfig: List<MultiTemplateElementDto>? = emptyList()

) {
    constructor(dto: NotificationTemplateDto) : this(
        dto.id,
        dto.name,
        dto.notificationTitle,
        dto.model,
        dto.multiTemplateConfig
    )
}
