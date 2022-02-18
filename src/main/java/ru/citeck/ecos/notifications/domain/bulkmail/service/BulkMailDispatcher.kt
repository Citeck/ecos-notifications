package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.springframework.stereotype.Service

//TODO: нужен ли этот класс?
@Service
class BulkMailDispatcher(
    private val bulkMailService: BulkMailService,
    private val bulkMailDao: BulkMailDao
) {

    fun dispatch(extId: String) {
        val bulkMail = bulkMailDao.findByExtId(extId)
            ?: throw IllegalArgumentException("Bulk mail with id $extId not found")

        bulkMailService.dispatch(bulkMail)
    }

    fun calculateRecipients(extId: String) {
        val bulkMail = bulkMailDao.findByExtId(extId)
            ?: throw IllegalArgumentException("Bulk mail with id $extId not found")

        bulkMailService.recalculateRecipients(bulkMail)
    }

}
