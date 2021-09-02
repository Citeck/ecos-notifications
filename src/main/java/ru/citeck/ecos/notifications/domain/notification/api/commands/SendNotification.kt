package ru.citeck.ecos.notifications.domain.notification.api.commands

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.notifications.domain.event.dto.ErrorInfo
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.service.FailureNotificationService
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult

@Service
class SendNotificationCommandExecutor(
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val failureNotificationService: FailureNotificationService,
    private val commandsServiceFactory: CommandsServiceFactory,
    private val notificationEventService: NotificationEventService
) : CommandExecutor<SendNotificationCommand> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun execute(command: SendNotificationCommand): Any? {
        return try {
            unsafeSendNotificationCommandExecutor.execute(command)
        } catch (e: Exception) {
            log.error("Failed execute notification command", e)

            failureNotificationService.holdFailure(command, e)

            val currentUser = commandsServiceFactory.commandCtxManager.getCurrentUser()
            val failureEventInfo = prepareFailureEventInfo(command, e)

            notificationEventService.emitSendFailure(
                failureEventInfo,
                currentUser
            )

            SendNotificationResult("error", e.message ?: "")
        }
    }

    private fun prepareFailureEventInfo(command: SendNotificationCommand, e: Exception): NotificationEventDto {

        val lyingFitNotification = FitNotification(
            body = "",
            title = "",
            recipients = command.recipients,
            from = command.from,
            cc = command.cc,
            bcc = command.bcc
        )
        val errorInfo = ErrorInfo(
            message = ExceptionUtils.getMessage(e),
            stackTrace = ExceptionUtils.getStackTrace(e)
        )

        return NotificationEventDto(
            rec = NotificationCommandUtils.resolveNotificationRecord(command.record),
            notificationType = command.type,
            notification = lyingFitNotification,
            model = command.model,
            error = errorInfo
        )
    }

}

