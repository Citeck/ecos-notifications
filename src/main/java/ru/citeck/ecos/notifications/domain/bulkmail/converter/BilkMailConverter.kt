package ru.citeck.ecos.notifications.domain.bulkmail.converter

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.bulkmail.api.records.BulkMailRecords
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailEntity
import ru.citeck.ecos.records2.RecordRef
import java.util.*

fun BulkMailEntity.toDto(): BulkMailDto {
    return BulkMailDto(
        id = id!!,
        extId = extId ?: "",
        record = RecordRef.valueOf(record),
        template = RecordRef.valueOf(template),
        type = type!!,
        title = title,
        body = body,
        recipientsData = ObjectData.create(recipientsData),
        config = ObjectData.create(config),
        status = status!!,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate
    )
}

fun BulkMailDto.toEntity(): BulkMailEntity {
    return BulkMailEntity(
        id = id,
        extId = extId ?: UUID.randomUUID().toString(),
        record = record.toString(),
        template = template.toString(),
        type = type,
        title = title,
        body = body,
        recipientsData = recipientsData.toString(),
        config = config.toString(),
        status = status
    )
}

fun BulkMailRecords.BulkMailRecord.toDto(): BulkMailDto {
    return BulkMailDto(
        id = id,
        extId = extId ?: UUID.randomUUID().toString(),
        record = RecordRef.valueOf(record),
        template = RecordRef.valueOf(template),
        type = type!!,
        title = title,
        body = body,
        recipientsData = recipientsData!!,
        config = config,
        status = status ?: "new",
        createdBy = creator,
        createdDate = created,
        lastModifiedBy = modifier,
        lastModifiedDate = modified
    )
}
