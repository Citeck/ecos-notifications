package ru.citeck.ecos.notifications.domain.bulkmail.service

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao

/**
 * @author Roman Makarskiy
 */
@Component
class BulkMailStatusSynchronizer(
    private val bulkMailDao: BulkMailDao,
    private val notificationDao: NotificationDao
) {

    private val log = KotlinLogging.logger {}

    private val statusesToFind = listOf(BulkMailStatus.WAIT_FOR_DISPATCH, BulkMailStatus.TRYING_TO_DISPATCH)

    //TODO: scheduled
    fun sync() {
        val bulkMails = bulkMailDao.findAllByStatuses(statusesToFind)

        log.debug { "Found bulk mails: $bulkMails" }

        bulkMails.forEach { bulkMail ->
            val all = notificationDao.getAll()

            val notificationsSummary = notificationDao.getBulkMailStateSummary(bulkMail.recordRef.toString())

            log.debug { "Found notification state summary for ${bulkMail.recordRef}: $notificationsSummary" }

            when {
                notificationsSummary.containsKey(NotificationState.ERROR) -> {
                    setBulkMailStatus(bulkMail, BulkMailStatus.TRYING_TO_DISPATCH)
                }

                notificationsSummary.containsKey(NotificationState.EXPIRED) -> {
                    setBulkMailStatus(bulkMail, BulkMailStatus.ERROR)
                }

                notificationsSummary.containsKey(NotificationState.WAIT_FOR_DISPATCH) -> {
                    setBulkMailStatus(bulkMail, BulkMailStatus.WAIT_FOR_DISPATCH)
                }

                notificationsSummary.containsKey(NotificationState.SENT) -> {
                    setBulkMailStatus(bulkMail, BulkMailStatus.SENT)
                }

            }
        }
    }

    private fun setBulkMailStatus(bulkMail: BulkMailDto, status: BulkMailStatus) {
        bulkMailDao.setStatus(bulkMail.extId!!, status)
    }

}
