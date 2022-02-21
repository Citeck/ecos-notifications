package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toDto
import ru.citeck.ecos.notifications.domain.bulkmail.converter.toEntity
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.repo.BulkMailRecipientRepository


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

}
