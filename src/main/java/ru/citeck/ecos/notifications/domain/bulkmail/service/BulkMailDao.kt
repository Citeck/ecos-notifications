package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.predicate.toValueModifiedSpec
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Service
@Transactional
class BulkMailDao(
    private val bulkMailRepository: BulkMailRepository
) {

    fun save(dto: BulkMailDto): BulkMailDto {
        return bulkMailRepository.save(
            dto.toEntity()
        )
            .toDto()
    }

    fun setStatus(extId: String, status: BulkMailStatus) {
        val entity = bulkMailRepository.findOneByExtId(extId).orElseThrow {
            IllegalArgumentException("Bulk mail with exit id: $extId not found")
        }

        entity.status = status.status

        bulkMailRepository.save(
            entity
        )
    }

    @Transactional(readOnly = true)
    fun findByExtId(extId: String): BulkMailDto? {
        val found = bulkMailRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): BulkMailDto? {
        val found = bulkMailRepository.findById(id)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    @Transactional(readOnly = true)
    fun findAllByStatuses(statuses: List<BulkMailStatus>): List<BulkMailDto> {
        return bulkMailRepository.findAllByStatusIn(statuses.map { it.status }).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<BulkMailDto> {
        val sorting = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return bulkMailRepository.findAll(toSpec(predicate), page)
            .map {
                it.toDto()
            }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(): List<BulkMailDto> {
        return bulkMailRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int): List<BulkMailDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return bulkMailRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getCount(predicate: Predicate): Long {
        val spec = toSpec<BulkMailEntity>(predicate)
        return if (spec != null) (bulkMailRepository.count(toSpec(predicate)).toInt()).toLong() else getCount()
    }

    @Transactional(readOnly = true)
    fun getCount(): Long {
        return bulkMailRepository.count()
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

        val predicateDto = PredicateUtils.convertToDto(pred, BulkMailPredicateDto::class.java)

        toLikeSpec(predicateDto.record, "record")
        toLikeSpec(predicateDto.moduleId, "extId")

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
        toLikeSpec(predicateDto.status, "status")
        toLikeSpec(predicateDto.title, "title")
        toLikeSpec(predicateDto.body, "body")

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
