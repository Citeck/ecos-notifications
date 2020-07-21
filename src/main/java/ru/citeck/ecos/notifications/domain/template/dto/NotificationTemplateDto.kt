package ru.citeck.ecos.notifications.domain.template.dto

import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData

open class NotificationTemplateDto(

    var id: String,
    var name: String? = null,
    var notificationTitle: MLText? = null,
    var model: ObjectData? = ObjectData.create()

) {
    constructor(dto: NotificationTemplateDto) : this(
        dto.id,
        dto.name,
        dto.notificationTitle,
        dto.model
    )
}
