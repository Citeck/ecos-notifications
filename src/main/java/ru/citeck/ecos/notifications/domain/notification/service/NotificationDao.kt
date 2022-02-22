package ru.citeck.ecos.notifications.domain.notification.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toDto
import ru.citeck.ecos.notifications.domain.notification.converter.toEntity
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.predicate.toValueModifiedSpec
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Service
@Transactional
class NotificationDao(
    private val notificationRepository: NotificationRepository
) {

    fun save(dto: NotificationDto): NotificationDto {
        return notificationRepository.save(dto.toEntity()).toDto()
    }

    fun saveAll(dto: List<NotificationDto>): List<NotificationDto> {
        return notificationRepository.saveAll(dto.map { it.toEntity() }).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun findAllEntitiesByState(notificationState: NotificationState): List<NotificationDto> {
        return notificationRepository.findAllByState(notificationState).map { it.toDto() }.toList()
    }

    @Transactional(readOnly = true)
    fun findAllToDispatch(): List<NotificationDto> {
        return notificationRepository.findAllToDispatch().map { it.toDto() }.toList()
    }

    @Transactional(readOnly = true)
    fun getByExtId(extId: String): NotificationDto? {
        val found = notificationRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): NotificationDto? {
        val found = notificationRepository.findById(id)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    @Transactional(readOnly = true)
    fun getBulkMailStateSummary(bulkMailRef: String): Map<NotificationState, Long> {
        return notificationRepository.getNotificationStateSummaryForBulkMail(bulkMailRef)
            .associateBy({ NotificationState.valueOf(it.getState()) }, { it.getCount() })
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<NotificationDto> {
        val sorting = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return notificationRepository.findAll(toSpec(predicate), page)
            .map {
                it.toDto()
            }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(): List<NotificationDto> {
        return notificationRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int): List<NotificationDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return notificationRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getCount(predicate: Predicate): Long {
        val spec = toSpec<NotificationEntity>(predicate)
        return if (spec != null) (notificationRepository.count(toSpec(predicate)).toInt()).toLong() else getCount()
    }

    private fun getCount(): Long {
        return notificationRepository.count()
    }

    private fun <T> toSpec(pred: Predicate): Specification<T>? {
        pred.toValueModifiedSpec<T>()?.let { return it }

        var spec: Specification<T>? = null

        fun toLikeSpec(value: String, attName: String) {
            if (value.isNotBlank()) {
                val likeSpec = Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get(attName)),
                        "%" + value.lowercase() + "%"
                    )
                }

                spec = spec?.or(likeSpec) ?: likeSpec
            }
        }

        val predicateDto = PredicateUtils.convertToDto(pred, NotificationPredicateDto::class.java)

        toLikeSpec(predicateDto.record, "record")
        toLikeSpec(predicateDto.moduleId, "extId")

        if (StringUtils.isNotBlank(predicateDto.state)) {
            val state = try {
                NotificationState.valueOf(predicateDto.state)
            } catch (e: Exception) {
                null
            }

            val stateSpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.equal(
                        root.get<String>("state"),
                        state
                    )
                }
            spec = spec?.or(stateSpec) ?: stateSpec
        }

        if (StringUtils.isNotBlank(predicateDto.type)) {
            val type = try {
                NotificationType.valueOf(predicateDto.type)
            } catch (e: Exception) {
                null
            }

            val typeSpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.equal(
                        root.get<String>("type"),
                        type
                    )
                }
            spec = spec?.or(typeSpec) ?: typeSpec
        }

        toLikeSpec(predicateDto.template, "template")

        return spec
    }

    data class NotificationPredicateDto(
        var record: String = "",
        var moduleId: String = "",
        var state: String = "",
        var type: String = "",
        var template: String = ""
    )

}
