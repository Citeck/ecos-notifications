package ru.citeck.ecos.notifications.domain.sender.dto

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicate
import java.time.Instant

data class NotificationsSenderDto(
    val id: String,
    val enabled: Boolean = false,
    val condition: Predicate? = null,
    val notificationType: NotificationType? = null,
    val order: Float? = null,
    val senderType: String? = null,
    val templates: List<RecordRef> = emptyList(),
    val senderConfig: ObjectData = ObjectData.create(),

    val createdBy: String? = null,
    val createdDate: Instant? = Instant.now(),
    val lastModifiedBy: String? = null,
    val lastModifiedDate: Instant? = Instant.now()) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationsSenderDto) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}
