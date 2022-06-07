package ru.citeck.ecos.notifications.domain.file.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude
import java.time.Instant

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
open class FileWithMeta(
    id: String,
    data: ByteArray? = null,
    var modifier: String? = null,
    var modified: Instant? = null,
    var creator: String? = null,
    var created: Instant? = null
) : FileDto(id, data) {
    constructor(dto: FileWithMeta) : this(
        dto.id,
        dto.data,
        dto.modifier,
        dto.modified,
        dto.creator,
        dto.created
    )

    override fun toString(): String {
        return "FileWithMeta(id=$id)"
    }
}
