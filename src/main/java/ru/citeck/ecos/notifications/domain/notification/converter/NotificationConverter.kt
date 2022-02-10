package ru.citeck.ecos.notifications.domain.notification.converter

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import javax.annotation.PostConstruct

@Component
class NotificationConverter(
    val recordsService: RecordsService
) {

    @PostConstruct
    private fun init() {
        notificationConverter = this
    }

}

private lateinit var notificationConverter: NotificationConverter

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

fun NotificationDto.toEntity(): NotificationEntity {
    return NotificationEntity(
        id = id,
        extId = extId,
        record = record.toString(),
        template = template.toString(),
        type = type,
        data = data,
        errorMessage = errorMessage,
        errorStackTrace = errorStackTrace,
        tryingCount = tryingCount,
        lastTryingDate = lastTryingDate,
        state = state
    )
}

fun Notification.toDtoWithState(state: NotificationState): NotificationDto {
    return NotificationDto(
        extId = id,
        type = type,
        record =  if (record is RecordRef) {
            record as RecordRef
        } else {
            RecordRef.valueOf(notificationConverter.recordsService.getAtt(record, "?id").asText())
        },
        template = templateRef,
        state = state,
        data = Json.mapper.toBytes(this),
        errorMessage = "",
        errorStackTrace = "",
        tryingCount = 0
    )
}

