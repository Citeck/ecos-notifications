package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientRepository
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.time.Instant
import javax.annotation.PostConstruct

@Service
@Transactional
class BulkMailDao(
    private val bulkMailRepository: BulkMailRepository,
    private val bulkMailRecipientRepository: BulkMailRecipientRepository,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory
) {

    private lateinit var searchConv: JpaSearchConverter<BulkMailEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(BulkMailEntity::class.java).build()
    }

    fun remove(dto: BulkMailDto) {
        val entity = dto.toEntity()

        bulkMailRecipientRepository.deleteAllByBulkMail(entity)
        bulkMailRepository.delete(entity)
    }

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

    fun updateModified(extId: String) {
        val entity = bulkMailRepository.findOneByExtId(extId).orElseThrow {
            IllegalArgumentException("Bulk mail with exit id: $extId not found")
        }

        entity.lastModifiedDate = Instant.now()

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
    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<BulkMailDto> {
        return searchConv.findAll(bulkMailRepository, predicate, max, skip, sort).map {
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
        return searchConv.getCount(bulkMailRepository, predicate)
    }

    @Transactional(readOnly = true)
    fun getCount(): Long {
        return bulkMailRepository.count()
    }
}
