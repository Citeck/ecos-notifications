package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRepository
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
class BulkMailDao(
    private val bulkMailRepository: BulkMailRepository
) {

    fun save(dto: BulkMailDto): BulkMailDto {
        return bulkMailRepository.save(
            dto.toEntity()
        )
            .toDto()
    }

    fun findByExtId(extId: String): BulkMailDto? {
        val found = bulkMailRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    fun findById(id: Long): BulkMailDto? {
        val found = bulkMailRepository.findById(id)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<BulkMailDto> {
        val sorting = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return bulkMailRepository.findAll(toSpec(predicate), page)
            .map {
                it.toDto()
            }.toList()
    }

    fun getAll(): List<BulkMailDto> {
        return bulkMailRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    fun getAll(max: Int, skip: Int): List<BulkMailDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return bulkMailRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    fun getCount(predicate: Predicate): Long {
        val spec = toSpec<BulkMailEntity>(predicate)
        return if (spec != null) (bulkMailRepository.count(toSpec(predicate)).toInt()).toLong() else getCount()
    }

    private fun getCount(): Long {
        return bulkMailRepository.count()
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

        val predicateDto = PredicateUtils.convertToDto(pred, BulkMailPredicateDto::class.java)
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

        if (StringUtils.isNotBlank(predicateDto.status)) {
            val statusSpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("status")),
                        "%" + predicateDto.status.lowercase() + "%"
                    )
                }
            spec = spec?.or(statusSpec) ?: statusSpec
        }

        if (StringUtils.isNotBlank(predicateDto.title)) {
            val titleSpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("title")),
                        "%" + predicateDto.title.lowercase() + "%"
                    )
                }
            spec = spec?.or(titleSpec) ?: titleSpec
        }

        if (StringUtils.isNotBlank(predicateDto.body)) {
            val bodySpec =
                Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("body")),
                        "%" + predicateDto.body.lowercase() + "%"
                    )
                }
            spec = spec?.or(bodySpec) ?: bodySpec
        }

        return spec
    }

    data class BulkMailPredicateDto(
        var record: String = "",
        var moduleId: String = "",
        var status: String = "",
        var type: String = "",
        var template: String = "",
        var title: String = "",
        var body: String = ""
    )

}
