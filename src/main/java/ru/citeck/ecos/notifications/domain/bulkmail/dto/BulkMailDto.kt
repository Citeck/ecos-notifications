package ru.citeck.ecos.notifications.domain.bulkmail.dto

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import java.time.Instant

data class BulkMailDto(
    val id: Long? = null,

    val extId: String? = null,

    val recipientsData: ObjectData,

    val record: RecordRef,

    val template: RecordRef,

    val type: NotificationType,

    val title: String? = null,

    val body: String? = null,

    val config: ObjectData? = null,

    val status: String,

    val createdBy: String? = null,

    val createdDate: Instant? = Instant.now(),

    val lastModifiedBy: String? = null,

    val lastModifiedDate: Instant? = Instant.now()

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BulkMailDto

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return 31
    }
}
