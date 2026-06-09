package ru.citeck.ecos.notifications.domain.notification.api.commands

import io.github.oshai.kotlinlogging.KotlinLogging
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
import ru.citeck.ecos.notifications.domain.notification.api.records.NotificationRecords
import ru.citeck.ecos.notifications.domain.notification.service.NotificationCommandResultHolder
import ru.citeck.ecos.notifications.lib.RecipientsSendStrategy
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

@Service
class SendNotificationCommandExecutor(
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val notificationCommandResultHolder: NotificationCommandResultHolder,
    private val commandsServiceFactory: CommandsServiceFactory,
    private val notificationEventService: NotificationEventService,
    private val recordsService: RecordsService
) : CommandExecutor<SendNotificationCommand> {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val PERSON_ATT_EXT_PORTAL_URL = "extPortalUrl"
        private const val PERSON_ATT_EMAIL = "email"
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

    private fun executeImpl(srcCommand: SendNotificationCommand): Any {
        var result = SendNotificationResult(NotificationResultStatus.ERROR.value, "DEFAULT_RESULT")
        for (command in splitCommand(srcCommand)) {
            try {
                result = unsafeSendNotificationCommandExecutor.execute(command)
                notificationCommandResultHolder.holdSuccess(command, result)
            } catch (e: Exception) {

                log.error(e) { "Failed execute notification command" }

                notificationCommandResultHolder.holdError(command, e)

                val failureEventInfo = prepareFailureEventInfo(command, e)
                notificationEventService.emitSendFailure(failureEventInfo)

                result = SendNotificationResult(NotificationResultStatus.ERROR.value, e.message ?: "")
            }
        }
        return result
    }

    private fun splitCommand(command: SendNotificationCommand): List<SendNotificationCommand> {

        // The send strategy partitions the To list (recipients) only. cc/bcc are copies within a
        // single (shared) message and are never partitioned by the strategy. Therefore PER_RECIPIENT
        // is defined only when cc/bcc are empty; when cc/bcc are set the command is sent as a single
        // COMBINED message regardless of the strategy. This is not a temporary limitation but a
        // consequence of cc/bcc semantics.
        if (command.webUrl.isNotBlank() ||
            command.recipients.isEmpty() ||
            command.cc.isNotEmpty() ||
            command.bcc.isNotEmpty()
        ) {
            if (command.recipientsSendStrategy == RecipientsSendStrategy.PER_RECIPIENT &&
                (command.cc.isNotEmpty() || command.bcc.isNotEmpty())
            ) {
                // The only place where the strategy is intentionally ignored - make the contradiction
                // observable instead of letting it silently fall back to a single message.
                log.warn {
                    "PER_RECIPIENT strategy is requested, but cc/bcc are not empty, " +
                        "so the notification is sent as a single combined message. " +
                        "commandId=${command.id}, record=${command.record}"
                }
            }
            // We can't universally split emails when cc and bcc is not empty
            // but in future we can add this logic based on strategy from configuration
            return listOf(command)
        }

        val users = recordsService.query(
            RecordsQuery.create()
                .withSourceId(AppName.EMODEL + "/person")
                .withQuery(Predicates.inVals(PERSON_ATT_EMAIL, command.recipients))
                .build(),
            listOf(PERSON_ATT_EMAIL, PERSON_ATT_EXT_PORTAL_URL)
        )
        val portalUrlByEmail = users.getRecords().map {
            it[PERSON_ATT_EMAIL].asText() to it[PERSON_ATT_EXT_PORTAL_URL].asText()
        }.filter {
            it.first.isNotBlank() && it.second.isNotBlank()
        }.toMap()

        // CRITICAL INVARIANT: the split MUST happen here, in splitCommand (the safe executor),
        // BEFORE NotificationCommandResultHolder persists the record (data = JSON of the sub-command,
        // one record per sub-command with its own UUID). The split must NOT be pushed below the
        // persist step (into UnsafeSendNotificationCommandExecutor / NotificationSenderService /
        // EmailNotificationSender), because ErrorNotificationRepeater, on retry, reads the persisted
        // command from notification.data and re-executes it DIRECTLY via
        // UnsafeSendNotificationCommandExecutor, BYPASSING splitCommand. If data held a pre-split
        // command with all recipients, a retry would send a single email to everyone and duplicate
        // the already-delivered ones. Splitting here means each sub-command = its own record = its
        // own retry unit, so a partial failure retries only the failed recipients, with no duplicates.
        if (command.recipientsSendStrategy == RecipientsSendStrategy.PER_RECIPIENT) {
            // PER_RECIPIENT is a finer partition than the webUrl one below, so it replaces it.
            // A fresh UUID per sub-command and createdFrom mirror the existing webUrl branch.
            return command.recipients.map { recipient ->
                command.copy(
                    id = UUID.randomUUID().toString(),
                    webUrl = portalUrlByEmail[recipient] ?: "",
                    recipients = setOf(recipient),
                    createdFrom = EntityRef.create(AppName.NOTIFICATIONS, NotificationRecords.ID, command.id)
                )
            }
        }

        val parts = LinkedHashMap<RecipientsPartKey, MutableSet<String>>()
        for (recipient in command.recipients) {
            val webUrl = portalUrlByEmail[recipient] ?: ""
            parts.computeIfAbsent(RecipientsPartKey(webUrl)) { LinkedHashSet() }.add(recipient)
        }

        if (parts.size == 1 && parts.keys.first().webUrl == command.webUrl) {
            return listOf(command)
        }

        return parts.entries.map { entry ->
            command.copy(
                id = UUID.randomUUID().toString(),
                webUrl = entry.key.webUrl,
                recipients = entry.value,
                createdFrom = EntityRef.create(AppName.NOTIFICATIONS, NotificationRecords.ID, command.id)
            )
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
            error = errorInfo,
            sendingMeta = emptyMap()
        )
    }

    private data class RecipientsPartKey(
        val webUrl: String
    )
}
