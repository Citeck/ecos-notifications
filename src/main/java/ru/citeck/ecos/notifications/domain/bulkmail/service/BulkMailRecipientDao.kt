package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.bulkmail.converter.fromId
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailEntity
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientRepository
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef


@Service
@Transactional
class BulkMailRecipientDao(
    private val bulkMailRecipientRepository: BulkMailRecipientRepository
) {

    @Transactional(readOnly = true)
    fun findAllForBulkMail(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        return bulkMailRecipientRepository.findAllByBulkMail(bulkMail.toEntity()).map { it.toDto() }
    }

    fun updateForBulkMail(bulkMail: BulkMailDto, recipients: Set<BulkMailRecipientDto>) {
        val newRecipients = recipients.map { recipientsDto ->
            val entity = recipientsDto.toEntity()
            entity.bulkMailRef = bulkMail.recordRef.toString()
            entity.bulkMail = BulkMailEntity.fromId(bulkMail.id!!)

            entity
        }

        deleteAllForBulkMail(bulkMail)
        bulkMailRecipientRepository.saveAll(newRecipients)
    }

    private fun deleteAllForBulkMail(bulkMail: BulkMailDto) {
        bulkMailRecipientRepository.deleteAllByBulkMail(bulkMail.toEntity())
    }

}
