package ru.citeck.ecos.notifications.domain.notification.service

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.api.commands.UnsafeSendNotificationCommandExecutor
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import java.time.Instant

private const val INFINITY_TTL = -1

@Component
class ErrorNotificationRepeater(
    private val notificationRepository: NotificationRepository,
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val props: ApplicationProperties
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(fixedDelayString = "\${ecos-notifications.notification.delay}")
    fun handleErrors() {
        val activeErrors = notificationRepository.findAllByState(NotificationState.ERROR)

        if (activeErrors.isNotEmpty()) {
            log.info { "Found notification error. Count: ${activeErrors.size}" }
        }

        AuthContext.runAsSystem {
            activeErrors.forEach { error -> reexecuteCommand(error) }
        }
    }

    private fun reexecuteCommand(notification: NotificationEntity) {
        log.debug {
            "Reexecute notification error with id ${notification.id}. " +
                "Current trying count: ${notification.tryingCount}"
        }

        try {
            val currentTryCount = notification.tryingCount ?: 0

            if (currentTryCount > props.notification.minTryCount
                && props.notification.ttl > INFINITY_TTL
            ) {
                val created = notification.createdDate
                    ?: throw IllegalStateException("Created date cannot be null. $notification")
                val now = Instant.now()
                val diff = now.toEpochMilli() - created.toEpochMilli()

                if (diff > props.notification.ttl) {
                    notification.state = NotificationState.EXPIRED
                    notification.tryingCount =
                        if (notification.tryingCount != null) notification.tryingCount!!.plus(1) else 1
                    notification.lastTryingDate = Instant.now()

                    notificationRepository.save(notification)

                    log.warn { "Mark notification error ${notification.id} as expired" }

                    return
                }
            }

            val command = Json.mapper.read(notification.data, SendNotificationCommand::class.java)
                ?: throw IllegalStateException("Failed convert notification data to command. Notification: $notification")

            unsafeSendNotificationCommandExecutor.execute(command)

            notification.state = NotificationState.SENT
            notification.tryingCount = if (notification.tryingCount != null) notification.tryingCount!!.plus(1) else 1
            notification.lastTryingDate = Instant.now()

            notificationRepository.save(notification)

            log.info { "Successful re executing notification command. Notification id: ${notification.id}" }
        } catch (e: Exception) {
            log.error(
                "Failed reexecute notification command. Notification id: ${notification.id}",
                e
            )

            notification.tryingCount = if (notification.tryingCount != null) notification.tryingCount!!.plus(1) else 1
            notification.lastTryingDate = Instant.now()
            notification.errorMessage = ExceptionUtils.getMessage(e)
            notification.errorStackTrace = ExceptionUtils.getStackTrace(e)
            notification.state = NotificationState.ERROR

            notificationRepository.save(notification)
        }
    }

}
