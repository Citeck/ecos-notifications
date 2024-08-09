package ru.citeck.ecos.notifications.domain.bulkmail.converter

import org.apache.commons.lang3.LocaleUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.api.records.BulkMailRecords
import ru.citeck.ecos.notifications.domain.bulkmail.dto.*
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientRepository
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRepository
import ru.citeck.ecos.notifications.domain.bulkmail.service.RecipientInfo
import ru.citeck.ecos.notifications.domain.bulkmail.service.UserInfo
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*
import javax.annotation.PostConstruct

private const val AUTHORITY_GROUP_PREFIX = "GROUP_"

/**
 * @author Roman Makarskiy
 */
@Component
class BulkMailConverter(
    val bulkMailRepository: BulkMailRepository,
    val bulkMailRecipientRepository: BulkMailRecipientRepository
) {

    @PostConstruct
    private fun init() {
        converter = this
    }
}

private lateinit var converter: BulkMailConverter

fun BulkMailEntity.toDto(): BulkMailDto {
    return BulkMailDto(
        id = id!!,
        name = name,
        extId = extId ?: "",
        record = EntityRef.valueOf(record),
        template = EntityRef.valueOf(template),
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
    val explicitExtId = if (extId.isNullOrBlank()) UUID.randomUUID().toString() else extId
    val dto = this

    return converter.bulkMailRepository.findOneByExtId(explicitExtId).orElse(BulkMailEntity()).apply {
        id = dto.id
        name = dto.name
        extId = explicitExtId
        record = dto.record.toString()
        template = dto.template.toString()
        type = dto.type
        title = dto.title
        body = dto.body
        recipientsData = Json.mapper.toString(dto.recipientsData)
        batchSize = dto.config.batchConfig.size
        personalizedMails = dto.config.batchConfig.personalizedMails
        delayedSend = dto.config.delayedSend
        allTo = dto.config.allTo
        allCc = dto.config.allCc
        allBcc = dto.config.allBcc
        lang = dto.config.lang?.toString()
        status = dto.status
    }
}

fun BulkMailRecords.BulkMailRecord.toDto(): BulkMailDto {
    return BulkMailDto(
        id = id,
        name = name,
        extId = extId ?: UUID.randomUUID().toString(),
        record = EntityRef.valueOf(record),
        template = EntityRef.valueOf(template),
        type = type!!,
        title = title ?: "",
        body = body ?: "",
        recipientsData = recipientsData ?: BulkMailRecipientsDataDto(),
        config = config ?: BulkMailConfigDto(),
        status = status ?: BulkMailStatus.NEW.status,
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

fun BulkMailRecipientDto.toEntity(bulkMailDto: BulkMailDto): BulkMailRecipientEntity {
    val explicitExtId = if (extId.isNullOrBlank()) UUID.randomUUID().toString() else extId
    val dto = this

    return converter.bulkMailRecipientRepository.findOneByExtId(explicitExtId).orElse(BulkMailRecipientEntity()).apply {
        id = dto.id
        extId = explicitExtId
        record = dto.record.toString()
        address = dto.address
        name = dto.name
        bulkMailRef = bulkMailDto.recordRef.toString()
        bulkMail = BulkMailEntity.fromId(bulkMailDto.id!!)
    }
}

fun BulkMailRecipientEntity.toDto(): BulkMailRecipientDto {
    return BulkMailRecipientDto(
        id = id,
        extId = extId!!,
        bulkMailRef = EntityRef.valueOf(bulkMailRef),
        record = EntityRef.valueOf(record),
        address = address!!,
        name = name ?: "",
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate
    )
}

fun BulkMailEntity.Companion.fromId(id: Long): BulkMailEntity {
    return BulkMailEntity(id = id)
}

fun BulkMailRecipientsDataDto.Companion.from(data: ObjectData): BulkMailRecipientsDataDto {
    return from(data.toString())
}

fun BulkMailRecipientDto.Companion.from(
    address: String,
    bulkMailRef: EntityRef
): BulkMailRecipientDto {
    if (address.isBlank()) throw IllegalArgumentException("Address cannot be blank")

    return BulkMailRecipientDto(
        extId = UUID.randomUUID().toString(),
        address = address,
        bulkMailRef = bulkMailRef
    )
}

fun BulkMailRecipientDto.Companion.from(
    userInfo: UserInfo,
    bulkMailRef: EntityRef
): BulkMailRecipientDto {
    if (userInfo.email.isNullOrBlank()) throw IllegalArgumentException("Address email cannot be blank")
    if (userInfo.record == EntityRef.EMPTY) throw IllegalArgumentException("User record id cannot be empty")

    return BulkMailRecipientDto(
        extId = UUID.randomUUID().toString(),
        address = userInfo.email!!,
        name = userInfo.disp ?: "",
        record = userInfo.record ?: EntityRef.EMPTY,
        bulkMailRef = bulkMailRef
    )
}

fun BulkMailRecipientDto.Companion.from(
    recipientInfo: RecipientInfo,
    bulkMailRef: EntityRef
): BulkMailRecipientDto {
    if (recipientInfo.address.isNullOrBlank()) throw IllegalArgumentException("Address email cannot be blank")

    return BulkMailRecipientDto(
        extId = UUID.randomUUID().toString(),
        address = recipientInfo.address!!,
        name = recipientInfo.disp ?: "",
        record = recipientInfo.record ?: EntityRef.EMPTY,
        bulkMailRef = bulkMailRef
    )
}

fun BulkMailConfigDto.Companion.from(data: String): BulkMailConfigDto {
    return Json.mapper.read(data, BulkMailConfigDto::class.java)
        ?: throw IllegalArgumentException("Failed create BulkMailConfigDto from data:\n$data")
}

fun BulkMailConfigDto.Companion.from(data: ObjectData): BulkMailConfigDto {
    return from(data.toString())
}

fun EntityRef.isAuthorityGroupRef(): Boolean {
    return getLocalId().startsWith(AUTHORITY_GROUP_PREFIX)
}
