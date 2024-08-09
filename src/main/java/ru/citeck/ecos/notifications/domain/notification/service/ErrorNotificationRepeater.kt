package ru.citeck.ecos.notifications.domain.notification.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.api.commands.UnsafeSendNotificationCommandExecutor
import ru.citeck.ecos.notifications.domain.notification.converter.toNotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import java.time.Instant

private const val INFINITY_TTL = -1

@Component
class ErrorNotificationRepeater(
    private val notificationDao: NotificationDao,
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val props: ApplicationProperties
) {

    private val handleErrorLock = Object()

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(initialDelay = 10_000, fixedDelayString = "\${ecos-notifications.error-notification.delay}")
    fun handleErrors() {
        synchronized(handleErrorLock) {
            val activeErrors = notificationDao.findAllEntitiesByState(NotificationState.ERROR)

            if (activeErrors.isNotEmpty()) {
                log.info { "Found notification error. Count: ${activeErrors.size}" }
            }

            AuthContext.runAsSystem {
                activeErrors.forEach { error -> reexecuteCommand(error) }
            }
        }
    }

    private fun reexecuteCommand(notification: NotificationDto) {
        log.debug {
            "Reexecute notification error with id ${notification.id}. " +
                "Current trying count: ${notification.tryingCount}"
        }

        try {
            val currentTryCount = notification.tryingCount ?: 0

            if (currentTryCount > props.errorNotification.minTryCount &&
                props.errorNotification.ttl > INFINITY_TTL
            ) {
                val created = notification.createdDate
                    ?: throw IllegalStateException("Created date cannot be null. $notification")
                val now = Instant.now()
                val diff = now.toEpochMilli() - created.toEpochMilli()

                if (diff > props.errorNotification.ttl) {
                    val updatedNotification = notification.copy(
                        state = NotificationState.EXPIRED,
                        tryingCount = notification.tryingCount.plus(1),
                        lastTryingDate = Instant.now()
                    )

                    notificationDao.save(updatedNotification)

                    log.warn { "Mark notification error ${updatedNotification.id} as expired" }

                    return
                }
            }

            val command = Json.mapper.read(notification.data, SendNotificationCommand::class.java)
                ?: throw IllegalStateException(
                    "Failed convert notification data to command. Notification: $notification"
                )

            val result = unsafeSendNotificationCommandExecutor.execute(command)

            val updatedNotification = notification.copy(
                state = result.toNotificationState(),
                tryingCount = notification.tryingCount.plus(1),
                lastTryingDate = Instant.now()
            )

            notificationDao.save(updatedNotification)

            log.info { "Successful re executing notification command. Notification id: ${updatedNotification.id}" }
        } catch (e: Exception) {
            log.error("Failed reexecute notification command. Notification id: ${notification.id}", e)

            val updatedNotification = notification.copy(
                state = NotificationState.ERROR,
                tryingCount = notification.tryingCount.plus(1),
                lastTryingDate = Instant.now(),
                errorMessage = ExceptionUtils.getMessage(e),
                errorStackTrace = ExceptionUtils.getStackTrace(e)
            )

            notificationDao.save(updatedNotification)
        }
    }
}
