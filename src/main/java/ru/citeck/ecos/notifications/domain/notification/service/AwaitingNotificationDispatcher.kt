package ru.citeck.ecos.notifications.domain.notification.service

import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.service.NotificationService

/**
 * @author Roman Makarskiy
 */
@Component
class AwaitingNotificationDispatcher(
    private val notificationDao: NotificationDao,
    val notificationService: NotificationService,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(initialDelay = 10_000, fixedDelayString = "\${ecos-notifications.awaiting-dispatch.delay}")
    fun dispatchNotifications() {

        val toDispatch = notificationDao.findAllToDispatch()

        log.debug { "Found notifications to dispatch: ${toDispatch.size}" }

        AuthContext.runAsSystem {
            toDispatch.forEach { error -> dispatch(error) }
        }
    }

    private fun dispatch(notificationDto: NotificationDto) {
        log.debug {
            "Dispatch notification with id ${notificationDto.id}"
        }

        val notification = Json.mapper.read(notificationDto.data, Notification::class.java)
            ?: throw IllegalStateException(
                "Failed convert notification data to notification. Notification: " +
                    "$notificationDto"
            )

        log.debug {
            "Build notification from ${NotificationState.WAIT_FOR_DISPATCH} " +
                "\nInput:" +
                "\n${notificationDto.data?.let { String(it) }}" +
                "\nResult:" +
                "\n${notification}"
        }

        notificationService.send(notification)
    }

}
