package ru.citeck.ecos.notifications.domain.notification.converter

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.api.records.BulkMailRecords
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import java.time.Instant
import java.util.*
import javax.annotation.PostConstruct

/**
 * @author Roman Makarskiy
 */
@Component
class NotificationConverter(
    val recordsService: RecordsService,
    val notificationRepository: NotificationRepository
) {

    @PostConstruct
    private fun init() {
        converter = this
    }

}

private lateinit var converter: NotificationConverter

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
        bulkMailRef = RecordRef.valueOf(bulkMailRef),
        delayedSend = delayedSend,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate
    )
}

fun NotificationDto.toEntity(): NotificationEntity {
    val explicitExtId = extId.ifBlank { UUID.randomUUID().toString() }
    val dto = this

    return converter.notificationRepository.findOneByExtId(explicitExtId).orElse(NotificationEntity()).apply {
        id = dto.id
        extId = explicitExtId
        record = dto.record.toString()
        template = dto.template.toString()
        type = dto.type
        data = dto.data
        errorMessage = dto.errorMessage
        errorStackTrace = dto.errorStackTrace
        tryingCount = dto.tryingCount
        lastTryingDate = dto.lastTryingDate
        state = dto.state
        bulkMailRef = dto.bulkMailRef.toString()
        delayedSend = dto.delayedSend
    }
}

fun Notification.toDtoWithState(
    state: NotificationState,
    bulkMailRef: RecordRef = RecordRef.EMPTY,
    delayedSend: Instant? = null
): NotificationDto {
    return NotificationDto(
        extId = id,
        type = type,
        record = if (record is RecordRef) {
            record as RecordRef
        } else {
            RecordRef.valueOf(converter.recordsService.getAtt(record, "?id").asText())
        },
        template = templateRef,
        state = state,
        data = Json.mapper.toBytes(this),
        errorMessage = "",
        errorStackTrace = "",
        tryingCount = 0,
        bulkMailRef = bulkMailRef,
        delayedSend = delayedSend
    )
}

val BulkMailDto.recordRef: RecordRef
    get() = RecordRef.create("notifications", BulkMailRecords.ID, extId)

