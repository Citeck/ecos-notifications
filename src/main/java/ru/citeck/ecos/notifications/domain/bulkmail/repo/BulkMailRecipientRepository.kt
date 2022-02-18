package ru.citeck.ecos.notifications.domain.bulkmail.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface BulkMailRecipientRepository : JpaRepository<BulkMailRecipientEntity, Long>,
    JpaSpecificationExecutor<BulkMailRecipientEntity> {

    fun deleteAllByBulkMail(bulkMailEntity: BulkMailEntity)

    fun findAllByBulkMail(bulkMailEntity: BulkMailEntity): List<BulkMailRecipientEntity>

}
