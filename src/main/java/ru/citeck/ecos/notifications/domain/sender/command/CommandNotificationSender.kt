package ru.citeck.ecos.notifications.domain.sender.command

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderResult
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.senders.NotificationSenderType

/**
 * Send email notification to the command with type (config.commandType) at application (config.targetApp)
 */
@Component
class CommandNotificationSender : NotificationSender<CommandSenderConfig> {

    @Autowired
    private lateinit var commandsService: CommandsService

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun getSenderType(): String {
        return NotificationSenderType.COMMAND.type
    }

    override fun getNotificationType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun getConfigClass(): Class<CommandSenderConfig> {
        return CommandSenderConfig::class.java
    }

    override fun sendNotification(
        notification: FitNotification,
        config: CommandSenderConfig
    ): NotificationSenderResult {
        val result = commandsService.execute {
            targetApp = config.targetApp
            body = CmdFitNotification(notification)
            type = config.commandType
        }.get()

        result.errors.forEach {
            log.error { "Command ${config.commandType} execution error: ${it.message}\n${it.stackTrace}" }
        }

        val cmdResult = result.getResultAs(NotificationSenderSendStatus::class.java) ?: throw NotificationException(
            "Failed to get notification send status from command '${config.commandType}' execution"
        )

        return NotificationSenderResult(cmdResult, emptyMap())
    }
}
