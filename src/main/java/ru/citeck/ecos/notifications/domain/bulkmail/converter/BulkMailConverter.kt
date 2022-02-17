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

private const val ALFRESCO_APP = "alfresco"
private const val AUTHORITY_SRC_ID = "authority"
private const val PEOPLE_SRC_ID = "people"
private const val AUTHORITY_GROUP_PREFIX = "GROUP_"

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


/**
 * Select orgstruct component send to backend userName or groupName. We need convert it to full recordRef format.
 * TODO: migrate to model (microservice) people/groups after completion of development.
 */
fun BulkMailRecipientsDataDto.Companion.from(data: ObjectData): BulkMailRecipientsDataDto {
    fun convertRecipientsToFullFilledRefs(recipients: List<RecordRef>): List<RecordRef> {
        return recipients.map {
            var fullFilledRef = it

            if (fullFilledRef.appName.isBlank()) {
                fullFilledRef = fullFilledRef.addAppName(ALFRESCO_APP)
            }

            if (fullFilledRef.sourceId.isBlank()) {
                val sourceId = if (fullFilledRef.isAuthorityGroupRef()) AUTHORITY_SRC_ID else PEOPLE_SRC_ID
                fullFilledRef = fullFilledRef.withSourceId(sourceId)
            }

            fullFilledRef
        }
    }

    val dto = from(data.toString())
    return dto.copy(
        recipients = convertRecipientsToFullFilledRefs(dto.recipients)
    )
}

fun BulkMailConfigDto.Companion.from(data: String): BulkMailConfigDto {
    return Json.mapper.read(data, BulkMailConfigDto::class.java)
        ?: throw IllegalArgumentException("Failed create BulkMailConfigDto from data:\n$data")
}

fun BulkMailConfigDto.Companion.from(data: ObjectData): BulkMailConfigDto {
    return from(data.toString())
}

fun RecordRef.isAuthorityGroupRef(): Boolean {
    return id.startsWith(AUTHORITY_GROUP_PREFIX)
}
