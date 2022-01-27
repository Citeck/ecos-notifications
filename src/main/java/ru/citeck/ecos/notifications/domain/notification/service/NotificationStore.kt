package ru.citeck.ecos.notifications.domain.notification.service

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import java.time.Instant
import java.util.*

@Component
class NotificationStore(
    private val notificationRepository: NotificationRepository,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun holdError(command: SendNotificationCommand, throwable: Throwable) {
        val entity = NotificationEntity()
        entity.extId = UUID.randomUUID().toString()
        entity.type = command.type
        entity.record = command.record.toString()
        entity.template = command.templateRef.toString()
        entity.state = NotificationState.ERROR
        entity.errorMessage = ExceptionUtils.getMessage(throwable)
        entity.errorStackTrace = ExceptionUtils.getStackTrace(throwable)
        entity.data = Json.mapper.toBytes(command)
        entity.tryingCount = 0
        entity.lastTryingDate = Instant.now()

        log.debug { "Save error notification:/n$entity" }

        notificationRepository.save(entity)
    }

    fun holdSuccess(command: SendNotificationCommand) {
        val entity = NotificationEntity()
        entity.extId = UUID.randomUUID().toString()
        entity.type = command.type
        entity.record = command.record.toString()
        entity.template = command.templateRef.toString()
        entity.state = NotificationState.SENT
        entity.data = Json.mapper.toBytes(command)
        entity.tryingCount = 0
        entity.lastTryingDate = Instant.now()

        log.debug { "Save success notification:/n$entity" }

        notificationRepository.save(entity)
    }


}
