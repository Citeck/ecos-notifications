package ru.citeck.ecos.notifications.domain.notification.service

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import java.time.Instant

@Component
class NotificationCommandResultHolder(
    private val notificationDao: NotificationDao,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun holdError(command: SendNotificationCommand, throwable: Throwable) {
        log.debug { "hold error notification command:\n $command" }

        val existsNotifications = notificationDao.getByExtId(command.id)

        val toSave: NotificationDto = existsNotifications?.copy(
            type = command.type,
            record = command.record,
            template = command.templateRef,
            state = NotificationState.ERROR,
            errorMessage = ExceptionUtils.getMessage(throwable),
            errorStackTrace = ExceptionUtils.getStackTrace(throwable),
            data = Json.mapper.toBytes(command),
            tryingCount = existsNotifications.tryingCount.plus(1),
            lastTryingDate = Instant.now()
        )
            ?: NotificationDto(
                extId = command.id,
                type = command.type,
                record = command.record,
                template = command.templateRef,
                state = NotificationState.ERROR,
                errorMessage = ExceptionUtils.getMessage(throwable),
                errorStackTrace = ExceptionUtils.getStackTrace(throwable),
                data = Json.mapper.toBytes(command),
                tryingCount = 1,
                lastTryingDate = Instant.now()
            )

        log.debug { "Save error notification:\n$toSave" }

        notificationDao.save(toSave)
    }

    fun holdSuccess(command: SendNotificationCommand) {
        log.debug { "hold success notification command:\n $command" }

        val toSave = getDto(command, null)
        log.debug { "Save success notification:\n$toSave" }

        notificationDao.save(toSave)
    }

    fun holdSuccess(command: SendNotificationCommand, result: SendNotificationResult) {
        log.debug { "Hold success notification command:\n $command \nwith result $result" }

        val toSave = getDto(command, result)
        log.debug { "Save success notification:\n$toSave" }

        notificationDao.save(toSave)
    }

    fun getDto(command: SendNotificationCommand, result: SendNotificationResult?): NotificationDto {
        val existsNotifications = notificationDao.getByExtId(command.id)
        var state = NotificationState.SENT
        if (result != null && !result.result.isEmpty()) {
            try {
                val senderStatus = NotificationSenderSendStatus.valueOf(result.result)
                if (NotificationSenderSendStatus.BLOCKED.equals(senderStatus)) {
                   state = NotificationState.BLOCKED
                }
            } catch (e: java.lang.IllegalArgumentException) {
                log.warn { "Unkown send result '${result.result}'" }
            }
        }

        return existsNotifications?.copy(
            type = command.type,
            record = command.record,
            template = command.templateRef,
            state = state,
            errorMessage = "",
            errorStackTrace = "",
            data = Json.mapper.toBytes(command),
            tryingCount = existsNotifications.tryingCount.plus(1),
            lastTryingDate = Instant.now()
        )
            ?: NotificationDto(
                extId = command.id,
                type = command.type,
                record = command.record,
                template = command.templateRef,
                state = state,
                errorMessage = "",
                errorStackTrace = "",
                data = Json.mapper.toBytes(command),
                tryingCount = 1,
                lastTryingDate = Instant.now()
            )
    }

}
