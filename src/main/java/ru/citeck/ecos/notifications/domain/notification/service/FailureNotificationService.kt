package ru.citeck.ecos.notifications.domain.notification.service

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState
import ru.citeck.ecos.notifications.domain.notification.api.commands.UnsafeSendNotificationCommandExecutor
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationRepository
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import java.time.Instant

private const val INFINITY_TTL = -1

@Service
class FailureNotificationService(
    private val failureNotificationRepository: FailureNotificationRepository,
    private val unsafeSendNotificationCommandExecutor: UnsafeSendNotificationCommandExecutor,
    private val props: ApplicationProperties
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun holdFailure(command: SendNotificationCommand, throwable: Throwable) {
        val entity = FailureNotificationEntity()
        entity.state = FailureNotificationState.ERROR
        entity.errorMessage = ExceptionUtils.getMessage(throwable)
        entity.errorStackTrace = ExceptionUtils.getStackTrace(throwable)
        entity.data = Json.mapper.toBytes(command)
        entity.tryingCount = 0

        log.debug { "Save failure notification:/n$entity" }

        failureNotificationRepository.save(entity)
    }

    @Scheduled(fixedDelayString = "\${ecos-notifications.failure-notification.delay}")
    fun handleErrors() {
        val activeFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)

        if (activeFailures.isNotEmpty()) {
            log.info { "Found notification error. Count: ${activeFailures.size}" }
        }

        AuthContext.runAsSystem {
            activeFailures.forEach { failure -> reexecuteCommand(failure) }
        }
    }

    private fun reexecuteCommand(failure: FailureNotificationEntity) {
        log.debug { "Reexecute failure with id ${failure.id}. Current trying count: ${failure.tryingCount}" }

        try {
            val currentTryCount = failure.tryingCount ?: 0

            if (currentTryCount > props.failureNotification.minTryCount
                && props.failureNotification.ttl >= INFINITY_TTL
            ) {
                val created = failure.createdDate
                    ?: throw IllegalStateException("Created date cannot be null. $failure")
                val now = Instant.now()
                val diff = now.toEpochMilli() - created.toEpochMilli()

                if (diff > props.failureNotification.ttl) {
                    failure.state = FailureNotificationState.EXPIRED
                    failure.tryingCount = if (failure.tryingCount != null) failure.tryingCount!!.plus(1) else 1
                    failure.lastTryingDate = Instant.now()

                    failureNotificationRepository.save(failure)

                    log.warn { "Mark failure ${failure.id} as expired" }

                    return
                }
            }

            val command = Json.mapper.read(failure.data, SendNotificationCommand::class.java)
                ?: throw IllegalStateException("Failed convert failure data to command. Failure: $failure")

            unsafeSendNotificationCommandExecutor.execute(command)

            failure.state = FailureNotificationState.SENT
            failure.tryingCount = if (failure.tryingCount != null) failure.tryingCount!!.plus(1) else 1
            failure.lastTryingDate = Instant.now()

            failureNotificationRepository.save(failure)

            log.info { "Successful re executing notification command. Failure id: ${failure.id}" }
        } catch (e: Exception) {
            log.error("Failed reexecute notification command. Failure id: ${failure.id}", e)

            failure.tryingCount = if (failure.tryingCount != null) failure.tryingCount!!.plus(1) else 1
            failure.lastTryingDate = Instant.now()
            failure.errorMessage = ExceptionUtils.getMessage(e)
            failure.errorStackTrace = ExceptionUtils.getStackTrace(e)
            failure.state = FailureNotificationState.ERROR

            failureNotificationRepository.save(failure)
        }
    }

}
