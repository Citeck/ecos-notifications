package ru.citeck.ecos.notifications.domain.notification.converter

import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.records2.RecordRef

fun NotificationEntity.toDto(): NotificationDto {
    return NotificationDto(
        id = id!!,
        extId = extId ?: "",
        record = RecordRef.valueOf(record),
        template = RecordRef.valueOf(template),
        type = type,
        data = data,
        errorMessage = errorMessage ?: "",
        errorStackTrace = errorStackTrace ?: "",
        tryingCount = tryingCount ?: 0,
        lastTryingDate = lastTryingDate,
        state = state!!,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate
    )
}

