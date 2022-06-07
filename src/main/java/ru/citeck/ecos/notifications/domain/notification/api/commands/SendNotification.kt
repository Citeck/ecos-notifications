package ru.citeck.ecos.notifications.domain.notification.api.commands

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.domain.event.dto.ErrorInfo
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.notifications.domain.event.service.NotificationEventService
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.service.NotificationCommandResultHolder
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult

@Service
class SendNotificationCommandExecutor(
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val notificationCommandResultHolder: NotificationCommandResultHolder,
    private val commandsServiceFactory: CommandsServiceFactory,
    private val notificationEventService: NotificationEventService
) : CommandExecutor<SendNotificationCommand> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun execute(command: SendNotificationCommand): Any? {
        val currentCommandUser = commandsServiceFactory.commandCtxManager.getCurrentUser()

        // TODO: Are the ecos-commands supposed to be run themselves under the command user auth?
        return if (currentCommandUser.isNotBlank()) {
            AuthContext.runAs(currentCommandUser) {
                executeImpl(command)
            }
        } else {
            executeImpl(command)
        }
    }

    private fun executeImpl(command: SendNotificationCommand): Any {
        return try {
            val result = unsafeSendNotificationCommandExecutor.execute(command)

            notificationCommandResultHolder.holdSuccess(command)

            return result
        } catch (e: Exception) {
            log.error("Failed execute notification command", e)

            notificationCommandResultHolder.holdError(command, e)

            val failureEventInfo = prepareFailureEventInfo(command, e)
            notificationEventService.emitSendFailure(failureEventInfo)

            SendNotificationResult(NotificationResultStatus.ERROR.value, e.message ?: "")
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
