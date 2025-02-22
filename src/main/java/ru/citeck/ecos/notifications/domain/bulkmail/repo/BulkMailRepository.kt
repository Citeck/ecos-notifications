package ru.citeck.ecos.notifications.domain.bulkmail.repo

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.*

interface BulkMailRepository :
    JpaRepository<BulkMailEntity, Long>,
    JpaSpecificationExecutor<BulkMailEntity> {

    fun findOneByExtId(extId: String): Optional<BulkMailEntity>

    fun findAllByExtIdIsIn(extIds: List<String>): List<BulkMailEntity>

    fun findAllByStatusIn(statuses: List<String>, pageable: Pageable): List<BulkMailEntity>
}
