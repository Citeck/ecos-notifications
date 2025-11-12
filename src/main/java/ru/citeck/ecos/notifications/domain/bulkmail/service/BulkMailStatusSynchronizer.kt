package ru.citeck.ecos.notifications.domain.bulkmail.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.webapp.lib.spring.context.auth.RunAsSystem

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

    @RunAsSystem
    @Scheduled(initialDelay = 10_000, fixedDelayString = "\${ecos-notifications.bulk-mail.sync-status-delay}")
    fun sync() {

        var page = 0
        val batchSize = 10

        while (true) {
            val bulkMails = bulkMailDao.findAllByStatuses(statusesToFind, PageRequest.of(page, batchSize))
            if (bulkMails.isEmpty()) {
                break
            }

            log.debug { "Found bulk mails size: ${bulkMails.size}" }
            log.trace { "Found bulk mails: $bulkMails" }

            bulkMails.forEach { bulkMail ->
                val notificationsSummary = notificationDao.getBulkMailStateSummary(bulkMail.recordRef.toString())

                log.trace { "Found notification state summary for ${bulkMail.recordRef}: $notificationsSummary" }

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

                    notificationsSummary.containsKey(NotificationState.SENT) ||
                        notificationsSummary.containsKey(NotificationState.RECIPIENTS_NOT_FOUND) ||
                        notificationsSummary.containsKey(NotificationState.BLOCKED) -> {
                        setBulkMailStatus(bulkMail, BulkMailStatus.SENT)
                    }
                }
            }

            page++
        }
    }

    private fun setBulkMailStatus(bulkMail: BulkMailDto, status: BulkMailStatus) {
        log.trace { "Set new status: $status for ${bulkMail.recordRef}" }

        bulkMailDao.setStatus(bulkMail.extId!!, status)
    }
}
