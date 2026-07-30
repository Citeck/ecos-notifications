package ru.citeck.ecos.notifications.domain.notification.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toNotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.lib.NotificationConstants
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import java.time.Instant

@Component
class NotificationCommandResultHolder(
    private val notificationDao: NotificationDao,
    private val workspaceService: WorkspaceService,
    private val failureClassifier: NotificationFailureClassifier,
    private val retryPolicy: NotificationRetryPolicy,
    private val metrics: NotificationRetryMetrics
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val DEFAULT_WORKSPACE_ID = "default"
        private const val DEFAULT_WORKSPACE_PERSIST_VALUE = ""
    }

    fun holdError(command: SendNotificationCommand, throwable: Throwable) {
        log.debug { "hold error notification command:\n $command" }

        val existsNotifications = notificationDao.getByExtId(command.id)

        // a notification cancelled while this attempt was in flight (its bulk mail was deleted)
        // must not be resurrected: writing ERROR here would put it back into the retry pipeline
        // for the full budget. This early check only saves the work below - the authoritative
        // guard is the conditional update at the end of this method, because the cancellation
        // races with it. holdSuccess has no such guard on purpose - the message did go out,
        // so recording the delivery is the truthful outcome and it schedules nothing
        if (existsNotifications?.state == NotificationState.CANCELLED) {
            log.info { "Notification ${command.id} was cancelled, its error result is dropped" }
            return
        }

        val errorMessage = ExceptionUtils.getMessage(throwable)

        // state/tryingCount/lastTryingDate/nextRetryAt/firstErrorAt/failureKind are decided
        // by NotificationRetryPolicy below, tryingCount here must NOT include the failed attempt
        val base: NotificationDto = existsNotifications?.copy(
            type = command.type,
            record = command.record,
            template = command.templateRef,
            webUrl = command.webUrl,
            errorMessage = errorMessage,
            // identical multi-KB traces are not rewritten on every attempt
            errorStackTrace = if (existsNotifications.errorMessage == errorMessage) {
                existsNotifications.errorStackTrace
            } else {
                ExceptionUtils.getStackTrace(throwable)
            },
            data = Json.mapper.toBytes(command)
        )
            ?: NotificationDto(
                extId = command.id,
                workspace = command.calcNotificationWorkspacePersistValue(),
                type = command.type,
                record = command.record,
                template = command.templateRef,
                webUrl = command.webUrl,
                createdFrom = command.createdFrom,
                state = NotificationState.ERROR,
                errorMessage = errorMessage,
                errorStackTrace = ExceptionUtils.getStackTrace(throwable),
                data = Json.mapper.toBytes(command),
                tryingCount = 0
            )

        val failureKind = failureClassifier.classify(throwable)
        val toSave = retryPolicy.applyFailure(base, failureKind, Instant.now())

        log.debug { "Save error notification:\n$toSave" }

        // an existing row is updated conditionally: the row may have been cancelled after the
        // read above (bulk mail deleted mid-attempt), and an unconditional save would revive it
        if (toSave.id != null) {
            if (!notificationDao.saveFailureIfNotCancelled(toSave)) {
                log.info {
                    "Notification ${command.id} was cancelled or removed concurrently, " +
                        "its error result is dropped"
                }
                return
            }
        } else {
            notificationDao.save(toSave)
        }

        // a permanent first failure never reaches the repeater, so the transition is counted here
        metrics.recordTerminalState(toSave.state)
    }

    /**
     * @param partialDeliveryNote set when the message was accepted only for part of the
     * recipients: the notification is still successful (no retry is scheduled), the note is kept
     * as the error message to make the rejected recipients visible.
     */
    fun holdSuccess(
        command: SendNotificationCommand,
        result: SendNotificationResult,
        partialDeliveryNote: String? = null
    ) {
        log.debug { "Hold success notification command:\n $command \nwith result $result" }

        val existsNotifications = notificationDao.getByExtId(command.id)
        val state = result.toNotificationState()

        // the row leaves the retry pipeline: clear the schedule, the failure verdict and the
        // retry-window anchor. firstErrorAt must not survive a success - the same command id can
        // be executed again later, and a stale anchor would expire its first failure immediately
        val toSave = existsNotifications?.copy(
            type = command.type,
            record = command.record,
            template = command.templateRef,
            webUrl = command.webUrl,
            state = state,
            errorMessage = partialDeliveryNote ?: "",
            errorStackTrace = "",
            data = Json.mapper.toBytes(command),
            tryingCount = existsNotifications.tryingCount.plus(1),
            lastTryingDate = Instant.now(),
            nextRetryAt = null,
            firstErrorAt = null,
            failureKind = null
        )
            ?: NotificationDto(
                extId = command.id,
                workspace = command.calcNotificationWorkspacePersistValue(),
                type = command.type,
                record = command.record,
                template = command.templateRef,
                webUrl = command.webUrl,
                createdFrom = command.createdFrom,
                state = state,
                errorMessage = partialDeliveryNote ?: "",
                errorStackTrace = "",
                data = Json.mapper.toBytes(command),
                tryingCount = 1,
                lastTryingDate = Instant.now()
            )

        log.debug { "Save success notification:\n$toSave" }

        notificationDao.save(toSave)
    }

    /**
     * In persistence layer, the default workspace should be stored as an empty string.
     */
    private fun SendNotificationCommand.calcNotificationWorkspacePersistValue(): String {
        fun findWorkspaceFromDifferentSources(): String? {
            val processWorkspace = model[NotificationConstants.PROCESS_WORKSPACE_ATT]
            if (processWorkspace != null && processWorkspace is String) {
                return processWorkspace
            }

            if (templateRef.isNotEmpty()) {
                return workspaceService.convertToIdInWs(this.templateRef.getLocalId()).workspace
            }

            val recordTypeWorkspace = model[NotificationConstants.TYPE_WORKSPACE_ATT]
            if (recordTypeWorkspace != null && recordTypeWorkspace is String) {
                return recordTypeWorkspace.toEntityRef().getLocalId()
            }

            return null
        }

        val workspaceId = findWorkspaceFromDifferentSources() ?: DEFAULT_WORKSPACE_PERSIST_VALUE

        return if (workspaceId == DEFAULT_WORKSPACE_ID) {
            DEFAULT_WORKSPACE_PERSIST_VALUE
        } else {
            workspaceId
        }
    }
}
