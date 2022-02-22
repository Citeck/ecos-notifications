package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientRepository
import ru.citeck.ecos.notifications.predicate.toValueModifiedSpec
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

/**
 * @author Roman Makarskiy
 */
@Service
@Transactional
class BulkMailRecipientDao(
    private val bulkMailRecipientRepository: BulkMailRecipientRepository
) {

    @Transactional(readOnly = true)
    fun findByExtId(extId: String): BulkMailRecipientDto? {
        val found = bulkMailRecipientRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else null
    }

    @Transactional(readOnly = true)
    fun findAllForBulkMail(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        return bulkMailRecipientRepository.findAllByBulkMail(bulkMail.toEntity()).map { it.toDto() }
    }

    fun saveForBulkMail(recipientDto: BulkMailRecipientDto, bulkMailDto: BulkMailDto): BulkMailRecipientDto {
        return bulkMailRecipientRepository.save(recipientDto.toEntity(bulkMailDto)).toDto()
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<BulkMailRecipientDto> {
        val sorting = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return bulkMailRecipientRepository.findAll(toSpec(predicate), page)
            .map {
                it.toDto()
            }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(): List<BulkMailRecipientDto> {
        return bulkMailRecipientRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int): List<BulkMailRecipientDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return bulkMailRecipientRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getCount(predicate: Predicate): Long {
        val spec = toSpec<BulkMailRecipientEntity>(predicate)
        return if (spec != null) (bulkMailRecipientRepository.count(toSpec(predicate)).toInt()).toLong() else getCount()
    }

    private fun getCount(): Long {
        return bulkMailRecipientRepository.count()
    }

    fun updateForBulkMail(bulkMail: BulkMailDto, recipients: Set<BulkMailRecipientDto>): List<BulkMailRecipientDto> {
        val newRecipients = recipients.map { recipientDto ->
            recipientDto.toEntity(bulkMail)
        }

        deleteAllForBulkMail(bulkMail)

        return bulkMailRecipientRepository.saveAll(newRecipients).map { it.toDto() }
    }

    private fun deleteAllForBulkMail(bulkMail: BulkMailDto) {
        bulkMailRecipientRepository.deleteAllByBulkMail(bulkMail.toEntity())
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

        val predicateDto = PredicateUtils.convertToDto(pred, BulkMailRecipientPredicateDto::class.java)

        toLikeSpec(predicateDto.record, "record")
        toLikeSpec(predicateDto.moduleId, "extId")
        toLikeSpec(predicateDto.bulkMailRef, "bulkMailRef")
        toLikeSpec(predicateDto.name, "name")
        toLikeSpec(predicateDto.address, "address")

        return spec
    }

    data class BulkMailRecipientPredicateDto(
        var record: String = "",
        var moduleId: String = "",
        var bulkMailRef: String = "",
        var name: String = "",
        var address: String = ""
    )

}
