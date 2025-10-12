package ru.citeck.ecos.notifications.domain.template.dto

import ru.citeck.ecos.commons.data.MLText

open class NotificationTemplateDto(

    var id: String,
    var workspace: String = "",
    var name: String? = null,
    var notificationTitle: MLText? = null,
    var tags: List<String> = emptyList(),
    var model: Map<String, String>? = emptyMap(),
    var multiTemplateConfig: List<MultiTemplateElementDto>? = emptyList()

) {
    constructor(dto: NotificationTemplateDto) : this(
        dto.id,
        dto.workspace,
        dto.name,
        dto.notificationTitle,
        dto.tags,
        dto.model,
        dto.multiTemplateConfig
    )

    override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as NotificationTemplateDto

        if (id != other.id ||
            workspace != other.workspace ||
            name != other.name ||
            notificationTitle != other.notificationTitle ||
            model != other.model ||
            multiTemplateConfig != other.multiTemplateConfig
        ) {

            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + workspace.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (notificationTitle?.hashCode() ?: 0)
        result = 31 * result + (model?.hashCode() ?: 0)
        result = 31 * result + (multiTemplateConfig?.hashCode() ?: 0)
        return result
    }
}
