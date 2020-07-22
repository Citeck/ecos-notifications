package ru.citeck.ecos.notifications.domain.notification.command

import org.apache.commons.lang3.LocaleUtils
import org.springframework.stereotype.Service
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.notifications.domain.notification.DEFAULT_LOCALE
import ru.citeck.ecos.notifications.domain.notification.Notification
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.service.NotificationException
import ru.citeck.ecos.notifications.service.NotificationService
import ru.citeck.ecos.records2.RecordRef


@Service
class SendNotificationCommandExecutor(
    val notificationService: NotificationService,
    val notificationTemplateService: NotificationTemplateService
) : CommandExecutor<NotificationSendCommand> {

    override fun execute(notificationSendCommand: NotificationSendCommand): Any? {
        val templateId = notificationSendCommand.templateRef.id

        val template = notificationTemplateService.findById(templateId).orElseThrow {
            NotificationException("Template with id: <$templateId> not found}")
        }

        val locale = if (notificationSendCommand.lang.isNullOrEmpty()) DEFAULT_LOCALE else LocaleUtils.toLocale(notificationSendCommand.lang)

        val notification = Notification(
            type = notificationSendCommand.type,
            locale = locale,
            recipients = notificationSendCommand.recipients,
            model = notificationSendCommand.model,
            from = notificationSendCommand.from,
            template = template
        )

        notificationService.send(notification)

        return NotificationSendResponse("ok", "")
    }

}


data class NotificationSendResponse(
    val status: String,
    val error: String
)

@CommandType("ecos.notifications.send")
data class NotificationSendCommand(
    val templateRef: RecordRef,
    val type: NotificationType,
    val lang: String?,
    val recipients: List<String>,
    val model: Map<String, Any>,
    val from: String
)

