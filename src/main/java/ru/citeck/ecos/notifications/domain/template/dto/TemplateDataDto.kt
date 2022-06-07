package ru.citeck.ecos.notifications.domain.template.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
data class TemplateDataDto(
    val name: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TemplateDataDto

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "TemplateDataDto(name='$name')"
    }
}
