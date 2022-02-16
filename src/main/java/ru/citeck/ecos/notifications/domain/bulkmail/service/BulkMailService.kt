package ru.citeck.ecos.notifications.domain.bulkmail.service

import org.apache.commons.collections4.ListUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.notification.service.AwaitNotificationService
import ru.citeck.ecos.notifications.lib.Notification

@Service
class BulkMailService(
    private val awaitNotificationService: AwaitNotificationService,
    private val recipientsFinder: RecipientsFinder
) {

    fun dispatch(bulkMailDto: BulkMailDto) {
        val notifications = buildNotifications(bulkMailDto)
        awaitNotificationService.sendToAwait(notifications, bulkMailDto)
    }

    fun buildNotifications(bulkMailDto: BulkMailDto): List<Notification> {
        val result = mutableListOf<Notification>()

        val recipientsParts = resolveRecipientsParts(bulkMailDto)

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

    fun resolveRecipientsParts(bulkMailDto: BulkMailDto): List<List<String>> {
        val allRecipients = recipientsFinder.resolveRecipients(bulkMailDto).toList()

        if (bulkMailDto.config.batchConfig.personalizedMails) {
            return ListUtils.partition(allRecipients, 1)
        }

        if (bulkMailDto.config.batchConfig.size > 0) {
            return ListUtils.partition(allRecipients, bulkMailDto.config.batchConfig.size)
        }

        return listOf(allRecipients)
    }


}
