package ru.citeck.ecos.notifications.domain.notification.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.notification.converter.toDtoWithState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.Notification
import java.time.Instant

/**
 * @author Roman Makarskiy
 */
@Service
class AwaitNotificationService(
    private val notificationDao: NotificationDao
) {

    fun sendToAwait(notification: Notification, delayedSend: Instant? = null): NotificationDto {
        val dto = notification.toDtoWithState(
            state = NotificationState.WAIT_FOR_DISPATCH,
            delayedSend = delayedSend
        )
        return notificationDao.save(dto)
    }

    fun sendToAwait(notifications: List<Notification>, bulkMail: BulkMailDto): List<NotificationDto> {
        return notificationDao.saveAll(
            notifications.map {
                it.toDtoWithState(
                    state = NotificationState.WAIT_FOR_DISPATCH,
                    bulkMailRef = bulkMail.recordRef,
                    delayedSend = bulkMail.config.delayedSend
                )
            }.toList()
        )
    }
}
