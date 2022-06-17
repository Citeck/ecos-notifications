package ru.citeck.ecos.notifications.domain.sender.service

import org.springframework.data.domain.Sort
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.records2.predicate.model.Predicate
import java.util.function.BiConsumer

interface NotificationsSenderService {

    fun getSenderById(id: String): NotificationsSenderDtoWithMeta?

    fun delete(id: String)

    fun save(senderDto: NotificationsSenderDto): NotificationsSenderDtoWithMeta

    fun getCount(): Long

    fun getCount(predicate: Predicate?): Long

    /**
     * Default sort is by ID
     */
    fun getAll(maxItems: Int, skipCount: Int, predicate: Predicate?, sort: Sort?):
        List<NotificationsSenderDtoWithMeta>

    fun getAll(): List<NotificationsSenderDtoWithMeta>

    /**
     * Default sort is by ORDER. Maximum result size is 10000
     */
    fun getAllEnabled(): List<NotificationsSenderDtoWithMeta>

    /**
     * Default sort is by ORDER. Maximum result size is 10000
     */
    fun getEnabled(predicate: Predicate?, sort: Sort?): List<NotificationsSenderDtoWithMeta>

    fun onSenderChanged(listener: BiConsumer<NotificationsSenderDto?, NotificationsSenderDto?>)

    fun removeAllByExtId(extIds: List<String>)
}
