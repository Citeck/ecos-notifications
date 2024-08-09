package ru.citeck.ecos.notifications.domain.sender.dto

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

data class NotificationsSenderDto(
    var id: String? = null,
    var name: String? = null,
    var enabled: Boolean = false,
    var condition: Predicate? = null,
    var notificationType: NotificationType? = null,
    var order: Float? = null,
    var senderType: String? = null,
    var templates: List<EntityRef> = emptyList(),
    var senderConfig: ObjectData = ObjectData.create(),

    var createdBy: String? = null,
    val createdDate: Instant? = Instant.now(),
    var lastModifiedBy: String? = null,
    val lastModifiedDate: Instant? = Instant.now()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationsSenderDto) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    fun getRef(): EntityRef {
        return EntityRef.create("notifications", NotificationsSenderRecordsDao.ID, id)
    }
}
