package ru.citeck.ecos.notifications.domain.notification.dto

import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import java.time.Instant

data class NotificationDto(

    val id: Long? = null,

    val extId: String,

    val record: RecordRef,

    val template: RecordRef,

    val type: NotificationType? = null,

    val data: ByteArray? = null,

    val errorMessage: String,

    val errorStackTrace: String,

    val bulkMailRef: RecordRef = RecordRef.EMPTY,

    val delayedSend: Instant? = null,

    val tryingCount: Int,

    val lastTryingDate: Instant? = null,

    val state: NotificationState,

    val createdBy: String? = null,

    val createdDate: Instant? = Instant.now(),

    val lastModifiedBy: String? = null,

    val lastModifiedDate: Instant? = Instant.now()

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NotificationDto

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "NotificationDto(id=$id, extId='$extId', record=$record, template=$template, type=$type, " +
            "errorMessage='$errorMessage', errorStackTrace='$errorStackTrace', bulkMailRef=$bulkMailRef, " +
            "delayedSend=$delayedSend, tryingCount=$tryingCount, lastTryingDate=$lastTryingDate, state=$state, " +
            "createdBy=$createdBy, createdDate=$createdDate, lastModifiedBy=$lastModifiedBy, " +
            "lastModifiedDate=$lastModifiedDate)"
    }
}
