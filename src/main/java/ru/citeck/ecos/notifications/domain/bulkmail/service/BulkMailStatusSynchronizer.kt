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

                val newStatus = resolveStatus(notificationsSummary) ?: return@forEach

                if (newStatus == BulkMailStatus.ERROR) {
                    log.info {
                        "Bulk mail ${bulkMail.recordRef} finished with failures: " +
                            "sent=${notificationsSummary[NotificationState.SENT] ?: 0}, " +
                            "failed=${notificationsSummary[NotificationState.FAILED] ?: 0}, " +
                            "expired=${notificationsSummary[NotificationState.EXPIRED] ?: 0}, " +
                            "cancelled=${notificationsSummary[NotificationState.CANCELLED] ?: 0}"
                    }
                }

                setBulkMailStatus(bulkMail, newStatus)
            }

            page++
        }
    }

    /**
     * Explicit priority chain:
     * 1. any ERROR — retries are still in progress, keep TRYING_TO_DISPATCH;
     * 2. any WAIT_FOR_DISPATCH — rows are still queued for sending;
     * 3. only when nothing is in flight anymore do terminal failures (EXPIRED/FAILED)
     *    flip the bulk mail to ERROR — a single EXPIRED row must not mark the whole
     *    bulk mail ERROR while other rows are still being sent;
     * 4. otherwise every row is settled (SENT/RECIPIENTS_NOT_FOUND/BLOCKED/CANCELLED) — SENT.
     */
    private fun resolveStatus(summary: Map<NotificationState, Long>): BulkMailStatus? {
        if (summary.isEmpty()) {
            return null
        }
        return when {
            summary.containsKey(NotificationState.ERROR) -> BulkMailStatus.TRYING_TO_DISPATCH

            summary.containsKey(NotificationState.WAIT_FOR_DISPATCH) -> BulkMailStatus.WAIT_FOR_DISPATCH

            summary.containsKey(NotificationState.EXPIRED) ||
                summary.containsKey(NotificationState.FAILED) -> BulkMailStatus.ERROR

            else -> BulkMailStatus.SENT
        }
    }

    private fun setBulkMailStatus(bulkMail: BulkMailDto, status: BulkMailStatus) {
        log.trace { "Set new status: $status for ${bulkMail.recordRef}" }

        bulkMailDao.setStatus(bulkMail.extId!!, status)
    }
}
