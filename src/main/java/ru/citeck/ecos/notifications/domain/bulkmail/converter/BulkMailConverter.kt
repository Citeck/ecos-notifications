package ru.citeck.ecos.notifications.domain.bulkmail.converter

import org.apache.commons.lang3.LocaleUtils
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.api.records.BulkMailRecords
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailBatchConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
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
        title = title ?: "",
        body = body ?: "",
        recipientsData = recipientsData?.let { BulkMailRecipientsDataDto.from(it) } ?: BulkMailRecipientsDataDto(),
        config = BulkMailConfigDto(
            batchConfig = BulkMailBatchConfigDto(
                size = batchSize,
                personalizedMails = personalizedMails
            ),
            delayedSend = delayedSend,
            allTo = allTo,
            allCc = allCc,
            allBcc = allBcc,
            lang = LocaleUtils.toLocale(lang)
        ),
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
        recipientsData = Json.mapper.toString(recipientsData),
        batchSize = config.batchConfig.size,
        personalizedMails = config.batchConfig.personalizedMails,
        delayedSend = config.delayedSend,
        allTo = config.allTo,
        allCc = config.allCc,
        allBcc = config.allBcc,
        lang = config.lang?.toString(),
        status = status,
    )
}

fun BulkMailRecords.BulkMailRecord.toDto(): BulkMailDto {
    return BulkMailDto(
        id = id,
        extId = extId ?: UUID.randomUUID().toString(),
        record = RecordRef.valueOf(record),
        template = RecordRef.valueOf(template),
        type = type!!,
        title = title ?: "",
        body = body ?: "",
        recipientsData = recipientsData?.let { BulkMailRecipientsDataDto.from(it) } ?: BulkMailRecipientsDataDto(),
        config = config?.let { BulkMailConfigDto.from(it) } ?: BulkMailConfigDto(),
        //TODO: default from constants?
        status = status ?: "new",
        createdBy = creator,
        createdDate = created,
        lastModifiedBy = modifier,
        lastModifiedDate = modified
    )
}

fun BulkMailRecipientsDataDto.Companion.from(data: String): BulkMailRecipientsDataDto {
    return Json.mapper.read(data, BulkMailRecipientsDataDto::class.java)
        ?: throw IllegalArgumentException("Failed create BulkMailRecipientsDataDto from data:\n$data")
}

fun BulkMailRecipientsDataDto.Companion.from(data: ObjectData): BulkMailRecipientsDataDto {
    return from(data.toString())
}

fun BulkMailConfigDto.Companion.from(data: String): BulkMailConfigDto {
    return Json.mapper.read(data, BulkMailConfigDto::class.java)
        ?: throw IllegalArgumentException("Failed create BulkMailConfigDto from data:\n$data")
}

fun BulkMailConfigDto.Companion.from(data: ObjectData): BulkMailConfigDto {
    return from(data.toString())
}
