package ru.citeck.ecos.notifications.domain.bulkmail.dto

import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

data class BulkMailRecipientDto(

    val id: Long? = null,

    val extId: String? = null,

    val bulkMailRef: EntityRef = EntityRef.EMPTY,

    val record: EntityRef = EntityRef.EMPTY,

    val address: String,

    val name: String = "",

    val createdBy: String? = null,

    val createdDate: Instant? = Instant.now(),

    val lastModifiedBy: String? = null,

    val lastModifiedDate: Instant? = Instant.now()
) {
    companion object;

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BulkMailRecipientDto) return false

        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}
