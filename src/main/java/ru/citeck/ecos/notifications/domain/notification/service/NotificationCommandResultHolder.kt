package ru.citeck.ecos.notifications.domain.notification.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toNotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
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
            webUrl = command.webUrl,
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
                webUrl = command.webUrl,
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

    fun holdSuccess(command: SendNotificationCommand, result: SendNotificationResult) {
        log.debug { "Hold success notification command:\n $command \nwith result $result" }

        val existsNotifications = notificationDao.getByExtId(command.id)
        val state = result.toNotificationState()

        val toSave = existsNotifications?.copy(
            type = command.type,
            record = command.record,
            template = command.templateRef,
            webUrl = command.webUrl,
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
                webUrl = command.webUrl,
                createdFrom = command.createdFrom,
                state = state,
                errorMessage = "",
                errorStackTrace = "",
                data = Json.mapper.toBytes(command),
                tryingCount = 1,
                lastTryingDate = Instant.now()
            )

        log.debug { "Save success notification:\n$toSave" }

        notificationDao.save(toSave)
    }
}
