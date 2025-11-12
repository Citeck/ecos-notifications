package ru.citeck.ecos.notifications.domain.sender.converter

import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*
import javax.annotation.PostConstruct

@Component
class NotificationsSenderConverter(val notificationsSenderRepository: NotificationsSenderRepository) {
    @PostConstruct
    private fun init() {
        converter = this
    }
}

private lateinit var converter: NotificationsSenderConverter

fun NotificationsSenderEntity.toDto(): NotificationsSenderDto {
    return NotificationsSenderDto(
        id = extId ?: "",
        name = name,
        enabled = enabled,
        condition = mapper.read(condition, Predicate::class.java),
        notificationType = notificationType,
        order = order,
        senderType = senderType,
        templates = mapper.readList(templates, EntityRef::class.java),
        senderConfig = ObjectData.create(senderConfig),

        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate
    )
}

fun NotificationsSenderEntity.toDtoWithMeta(): NotificationsSenderDtoWithMeta {
    return NotificationsSenderDtoWithMeta(toDto())
}

fun NotificationsSenderDto.toEntity(): NotificationsSenderEntity {
    val dto = this
    var entity: NotificationsSenderEntity? = null
    if (StringUtils.isNoneBlank(id)) {
        entity = converter.notificationsSenderRepository.findOneByExtId(id!!).orElse(null)
    }

    val explicitExtId = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id
    if (entity == null) {
        entity = NotificationsSenderEntity(null, explicitExtId)
    }

    entity.name = dto.name
    entity.enabled = dto.enabled
    entity.condition = mapper.toString(dto.condition)
    entity.notificationType = dto.notificationType
    entity.order = dto.order
    entity.senderType = dto.senderType
    entity.templates = mapper.toString(dto.templates)
    entity.senderConfig = dto.senderConfig.toString()
    return entity
}

fun NotificationsSenderDtoWithMeta.toDto(): NotificationsSenderDto {
    return NotificationsSenderDto(
        id ?: UUID.randomUUID().toString(),
        name,
        enabled,
        condition,
        notificationType,
        order,
        senderType,
        templates,
        senderConfig,
        creator,
        created,
        modifier,
        modified
    )
}

fun NotificationsSenderRecordsDao.NotificationsSenderRecord.toDto(): NotificationsSenderDto {
    return NotificationsSenderDto(
        id ?: UUID.randomUUID().toString(),
        name,
        enabled,
        condition,
        notificationType,
        order,
        senderType,
        templates,
        senderConfig,
        creator,
        created,
        modifier,
        modified
    )
}

fun NotificationsSenderRecordsDao.NotificationsSenderRecord.toDtoWithMeta(): NotificationsSenderDtoWithMeta {
    return NotificationsSenderDtoWithMeta(toDto())
}
