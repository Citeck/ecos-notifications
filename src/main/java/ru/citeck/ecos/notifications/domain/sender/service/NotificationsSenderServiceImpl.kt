package ru.citeck.ecos.notifications.domain.sender.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.converter.toEntity
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer

@Service
class NotificationsSenderServiceImpl (val repository: NotificationsSenderRepository):
    NotificationsSenderService {
    private val changeListeners: MutableList<BiConsumer<NotificationsSenderDto?, NotificationsSenderDto?>> =
        CopyOnWriteArrayList()

    override fun getSenderById(id: String?): NotificationsSenderDtoWithMeta? {
        if (StringUtils.isBlank(id)) {
            null
        }
        val found = repository.findOneByExtId(id!!)
        return if (found.isPresent) {
            NotificationsSenderDtoWithMeta(found.get().toDto())
        } else null
    }

    @Transactional
    override fun delete(id: String) {
        repository.findOneByExtId(id).ifPresent(repository::delete)
    }

    @Transactional
    override fun save(senderDto: NotificationsSenderDto): NotificationsSenderDtoWithMeta? {
        val beforeSenderDto = getSenderById(senderDto.id)?.toDto()
        val entity = repository.save(senderDto.toEntity())
        val afterSenderDto = entity.toDto()

        for (listener in changeListeners) {
            listener.accept(beforeSenderDto, afterSenderDto)
        }
        return NotificationsSenderDtoWithMeta(afterSenderDto);
    }

    override fun onSenderChanged(listener: BiConsumer<NotificationsSenderDto?, NotificationsSenderDto?>) {
        changeListeners.add(listener)
    }

    override fun getCount(): Long {
        return repository.count()
    }

    override fun getCount(predicate: Predicate?): Long {
        TODO("Not yet implemented")
    }

    override fun getAll(maxItems: Int, skipCount: Int, predicate: Predicate?, sort: Sort?):
        List<NotificationsSenderDtoWithMeta?>? {
        if (maxItems == 0) {
            return emptyList()
        }
        val page = PageRequest.of(skipCount / maxItems, maxItems,
            sort ?: Sort.by(Sort.Direction.DESC, NotificationsSenderEntity.ID)
        )

        TODO("Not yet implemented")
    }

    fun toSpecification(predicate: Predicate?): Specification<NotificationsSenderEntity>? {
        if (predicate == null) {
            return null
        }
        TODO("Not yet implemented")
    }
}
