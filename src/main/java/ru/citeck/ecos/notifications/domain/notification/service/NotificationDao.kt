package ru.citeck.ecos.notifications.domain.notification.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toDto
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import java.time.Instant
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Service
class NotificationDao(
    private val notificationRepository: NotificationRepository
) {

    fun getByExtId(extId: String): NotificationDto? {
        val found = notificationRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    fun getById(id: Long): NotificationDto? {
        val found = notificationRepository.findById(id)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<NotificationDto> {
        val sorting = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return notificationRepository.findAll(toSpec(predicate), page)
            .map {
                it.toDto()
            }.toList()
    }

    fun getAll(): List<NotificationDto> {
        return notificationRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    fun getAll(max: Int, skip: Int): List<NotificationDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return notificationRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    fun getCount(predicate: Predicate): Long {
        val spec = toSpec<NotificationEntity>(predicate)
        return if (spec != null) (notificationRepository.count(toSpec(predicate)).toInt()).toLong() else getCount()
    }

    private fun getCount(): Long {
        return notificationRepository.count()
    }

    private fun <T> toSpec(pred: Predicate): Specification<T>? {

        if (pred is ValuePredicate) {
            if (RecordConstants.ATT_MODIFIED == pred.getAttribute() && ValuePredicate.Type.GT == pred.getType()) {
                val instant = Json.mapper.convert(pred.getValue(), Instant::class.java)
                if (instant != null) {
                    return Specification { root: Root<T>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
                        builder.greaterThan(
                            root.get<Any>("lastModifiedDate").`as`(
                                Instant::class.java
                            ), instant
                        )
                    }
                }
            }
        }

        val predicateDto = PredicateUtils.convertToDto(pred, NotificationPredicateDto::class.java)
        var spec: Specification<T>? = null

        if (StringUtils.isNotBlank(predicateDto.record)) {
            spec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("record")),
                        "%" + predicateDto.record.lowercase() + "%"
                    )
                }
        }

        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            val idSpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("extId")),
                        "%" + predicateDto.moduleId.lowercase() + "%"
                    )
                }
            spec = spec?.or(idSpec) ?: idSpec
        }

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

        if (StringUtils.isNotBlank(predicateDto.template)) {
            val templateSpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("template")),
                        "%" + predicateDto.template.lowercase() + "%"
                    )
                }
            spec = spec?.or(templateSpec) ?: templateSpec
        }

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
