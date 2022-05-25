package ru.citeck.ecos.notifications.domain.sender.command

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType

@Component
class CommandNotificationSender(): NotificationSender<CommandSenderConfig> {

    @Autowired
    private lateinit var commandsService: CommandsService

    companion object {
        const val SENDER_TYPE = "command"
        private val log = KotlinLogging.logger {}
    }

    override fun getSenderType(): String {
        return SENDER_TYPE
    }

    override fun getNotificationType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun sendNotification(notification: FitNotification, config: CommandSenderConfig):
        NotificationSenderSendStatus {
        //catch exceptions
        val result =  commandsService.execute {
            targetApp = config.targetApp
            body = CmdFitNotification(notification)
            type = config.commandType
        }.get()
        result.errors.forEach{
            log.error("Command {} execution error: {}\n{}", config.commandType, it.message, it.stackTrace) }
        val cmdResult : NotificationSenderSendStatus? = result.getResultAs(NotificationSenderSendStatus::class.java)
        return cmdResult ?:
            throw NotificationException(String.format(
                "Failed to get notification send status from command '%s' execution", config.commandType))
    }
}
