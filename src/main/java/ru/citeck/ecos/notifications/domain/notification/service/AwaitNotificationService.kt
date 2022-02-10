package ru.citeck.ecos.notifications.domain.notification.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toDtoWithState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.Notification

/**
 * @author Roman Makarskiy
 */
@Service
class AwaitNotificationService(
    private val notificationDao: NotificationDao
) {

    fun sendToAwait(notification: Notification): NotificationDto {
        val dto = notification.toDtoWithState(NotificationState.WAIT_FOR_DISPATCH)
        return notificationDao.save(dto)
    }

}
