package ru.citeck.ecos.notifications.domain.notification.api.commands

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.notifications.domain.notification.service.FailureNotificationService
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult

@Service
class SendNotificationCommandExecutor(
    val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    val failureNotificationService: FailureNotificationService
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
            SendNotificationResult("error", e.message ?: "")
        }
    }

}

