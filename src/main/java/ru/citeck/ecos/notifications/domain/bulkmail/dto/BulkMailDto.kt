package ru.citeck.ecos.notifications.domain.bulkmail.dto

import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

data class BulkMailDto(
    val id: Long? = null,

    var name: String? = null,

    val extId: String? = null,

    val recipientsData: BulkMailRecipientsDataDto = BulkMailRecipientsDataDto(),

    val config: BulkMailConfigDto = BulkMailConfigDto(),

    val record: EntityRef = EntityRef.EMPTY,

    val template: EntityRef = EntityRef.EMPTY,

    val type: NotificationType,

    val title: String = "",

    val body: String = "",

    val status: String = BulkMailStatus.NEW.status,

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
        return id?.hashCode() ?: 0
    }
}
