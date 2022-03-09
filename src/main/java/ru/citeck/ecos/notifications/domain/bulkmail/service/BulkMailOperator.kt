package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.apache.commons.collections4.ListUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.bulkmail.BulkMailStatus
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.notification.service.AwaitNotificationService
import ru.citeck.ecos.notifications.lib.Notification

@Service
class BulkMailOperator(
    private val bulkMailDao: BulkMailDao,
    private val awaitNotificationService: AwaitNotificationService,
    private val recipientsFinder: RecipientsFinder,
    private val bulkMailRecipientDao: BulkMailRecipientDao
) {

    fun dispatch(extId: String) {
        val bulkMail = bulkMailDao.findByExtId(extId)
            ?: throw IllegalArgumentException("Bulk mail with id $extId not found")

        dispatchNotificationToAwait(bulkMail)

        bulkMailDao.setStatus(bulkMail.extId!!, BulkMailStatus.WAIT_FOR_DISPATCH)
    }

    private fun dispatchNotificationToAwait(bulkMailDto: BulkMailDto) {
        val notifications = buildNotifications(bulkMailDto)
        if (notifications.isEmpty()) throw IllegalStateException("No notifications generated. Check recipients.")

        awaitNotificationService.sendToAwait(notifications, bulkMailDto)
    }

    fun calculateRecipients(extId: String) {
        val bulkMail = bulkMailDao.findByExtId(extId)
            ?: throw IllegalArgumentException("Bulk mail with id $extId not found")

        recalculateRecipients(bulkMail)
    }

    private fun recalculateRecipients(bulkMailDto: BulkMailDto) {
        val newRecipients = recipientsFinder.resolveRecipients(bulkMailDto)
        bulkMailRecipientDao.updateForBulkMail(bulkMailDto, newRecipients)
    }

    private fun buildNotifications(bulkMailDto: BulkMailDto): List<Notification> {
        val result = mutableListOf<Notification>()

        val recipientsParts = resolveRecipientsAddressesParts(bulkMailDto)

        recipientsParts.forEach { recipientsPart ->

            val notification: Notification

            //TODO: refactor
            if (bulkMailDto.config.allCc) {
                notification = Notification.Builder()
                    .record(bulkMailDto.record)
                    .title(bulkMailDto.title)
                    .body(bulkMailDto.body)
                    .templateRef(bulkMailDto.template)
                    .notificationType(bulkMailDto.type)
                    .cc(recipientsPart)
                    .lang(bulkMailDto.config.lang)
                    .build()
            } else if (bulkMailDto.config.allBcc) {
                notification = Notification.Builder()
                    .record(bulkMailDto.record)
                    .title(bulkMailDto.title)
                    .body(bulkMailDto.body)
                    .templateRef(bulkMailDto.template)
                    .notificationType(bulkMailDto.type)
                    .bcc(recipientsPart)
                    .lang(bulkMailDto.config.lang)
                    .build()
            } else {
                notification = Notification.Builder()
                    .record(bulkMailDto.record)
                    .title(bulkMailDto.title)
                    .body(bulkMailDto.body)
                    .templateRef(bulkMailDto.template)
                    .notificationType(bulkMailDto.type)
                    .recipients(recipientsPart)
                    .lang(bulkMailDto.config.lang)
                    .build()
            }

            result.add(notification)
        }

        return result
    }

    private fun resolveRecipientsAddressesParts(bulkMailDto: BulkMailDto): List<List<String>> {
        val allAddresses = bulkMailRecipientDao.findAllForBulkMail(bulkMailDto).map { it.address }
        if (allAddresses.isEmpty()) return emptyList()

        if (bulkMailDto.config.batchConfig.personalizedMails) {
            return ListUtils.partition(allAddresses, 1)
        }

        if (bulkMailDto.config.batchConfig.size > 0) {
            return ListUtils.partition(allAddresses, bulkMailDto.config.batchConfig.size)
        }

        return listOf(allAddresses)
    }

}
