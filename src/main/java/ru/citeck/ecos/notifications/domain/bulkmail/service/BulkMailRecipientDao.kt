package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import javax.annotation.PostConstruct

/**
 * @author Roman Makarskiy
 */
@Service
@Transactional
class BulkMailRecipientDao(
    private val bulkMailRecipientRepository: BulkMailRecipientRepository,
    @Lazy private val bulkMailDao: BulkMailDao,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory
) {

    private lateinit var searchConv: JpaSearchConverter<BulkMailRecipientEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(BulkMailRecipientEntity::class.java).build()
    }

    fun removeAllByExtId(extIds: List<String>) {
        bulkMailRecipientRepository.deleteAllByExtIdIn(extIds)
    }

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
    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<BulkMailRecipientDto> {
        return searchConv.findAll(bulkMailRecipientRepository, predicate, max, skip, sort).map {
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
        return searchConv.getCount(bulkMailRecipientRepository, predicate)
    }

    private fun getCount(): Long {
        return bulkMailRecipientRepository.count()
    }

    fun updateForBulkMail(bulkMail: BulkMailDto, recipients: Set<BulkMailRecipientDto>): List<BulkMailRecipientDto> {
        val newRecipients = recipients.map { recipientDto ->
            recipientDto.toEntity(bulkMail)
        }

        deleteAllForBulkMail(bulkMail)

        val result = bulkMailRecipientRepository.saveAll(newRecipients).map { it.toDto() }
        bulkMail.extId?.let { bulkMailDao.updateModified(it) }

        return result
    }

    private fun deleteAllForBulkMail(bulkMail: BulkMailDto) {
        bulkMailRecipientRepository.deleteAllByBulkMail(bulkMail.toEntity())
    }
}
