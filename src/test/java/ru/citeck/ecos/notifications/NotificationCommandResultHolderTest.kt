package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.NotificationCommandResultHolder
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.util.*

/**
 * Verifies the synchronous error path: [NotificationCommandResultHolder] classifies the
 * failure and applies the retry policy, so a persisted row immediately carries the correct
 * state (ERROR/FAILED) and retry schedule fields; success clears the row from the pipeline.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationCommandResultHolderTest : BaseMailTest() {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var resultHolder: NotificationCommandResultHolder

    @Autowired
    private lateinit var commandsService: CommandsService

    private fun buildCommand(templateId: String = "test-template") = SendNotificationCommand(
        id = UUID.randomUUID().toString(),
        record = EntityRef.EMPTY,
        templateRef = EntityRef.create("notifications", "template", templateId),
        type = NotificationType.EMAIL_NOTIFICATION,
        lang = "en",
        recipients = setOf("someUser@gmail.com"),
        model = templateModel,
        from = "testFrom@mail.ru"
    )

    private fun rowOf(command: SendNotificationCommand): NotificationEntity {
        return notificationRepository.findOneByExtId(command.id).get()
    }

    @Test
    fun `transient failure is scheduled for retry`() {
        greenMail.stop()

        val command = buildCommand()
        val before = Instant.now()

        commandsService.executeSync(command, "notifications")

        val row = rowOf(command)
        assertThat(row.state).isEqualTo(NotificationState.ERROR)
        assertThat(row.failureKind).isEqualTo(FailureKind.TRANSIENT)
        assertThat(row.tryingCount).isEqualTo(1)
        assertThat(row.firstErrorAt).isNotNull()
        assertThat(row.nextRetryAt).isNotNull()
        assertThat(row.nextRetryAt).isAfterOrEqualTo(before)
    }

    @Test
    fun `permanent failure goes straight to FAILED without retry schedule`() {
        val command = buildCommand("this-template-does-not-exist")

        commandsService.executeSync(command, "notifications")

        val row = rowOf(command)
        assertThat(row.state).isEqualTo(NotificationState.FAILED)
        assertThat(row.failureKind).isEqualTo(FailureKind.PERMANENT)
        assertThat(row.tryingCount).isEqualTo(1)
        assertThat(row.firstErrorAt).isNotNull()
        assertThat(row.nextRetryAt).isNull()
    }

    @Test
    fun `success clears retry schedule and failure verdict`() {
        greenMail.stop()

        val command = buildCommand()
        commandsService.executeSync(command, "notifications")

        var row = rowOf(command)
        assertThat(row.state).isEqualTo(NotificationState.ERROR)
        assertThat(row.nextRetryAt).isNotNull()
        assertThat(row.failureKind).isEqualTo(FailureKind.TRANSIENT)

        greenMail.start()
        commandsService.executeSync(command, "notifications")

        row = rowOf(command)
        assertThat(row.state).isEqualTo(NotificationState.SENT)
        assertThat(row.tryingCount).isEqualTo(2)
        assertThat(row.nextRetryAt).isNull()
        assertThat(row.failureKind).isNull()
        // the retry window is anchored at firstErrorAt: leaving it set would make the next
        // failure of this command id look older than retry-window and expire it at once
        assertThat(row.firstErrorAt).isNull()
    }

    /**
     * A notification cancelled while its attempt was in flight (its bulk mail was deleted meanwhile)
     * must not be put back into the retry pipeline by the failure of that attempt.
     */
    @Test
    fun `cancelled notification is not resurrected by a failed attempt`() {
        val command = buildCommand()
        val cancelled = notificationRepository.save(
            NotificationEntity(
                extId = command.id,
                state = NotificationState.CANCELLED,
                tryingCount = 1
            )
        )

        try {
            resultHolder.holdError(command, RuntimeException("smtp is down"))

            val row = rowOf(command)
            assertThat(row.state).isEqualTo(NotificationState.CANCELLED)
            assertThat(row.tryingCount).isEqualTo(1)
            assertThat(row.nextRetryAt).isNull()
        } finally {
            notificationRepository.deleteById(cancelled.id!!)
        }
    }

    @Test
    fun `stack trace is rewritten only when error message changes`() {
        val command = buildCommand()

        resultHolder.holdError(command, failFromFirstPlace())

        var row = rowOf(command)
        val firstTrace = row.errorStackTrace
        assertThat(firstTrace).contains("failFromFirstPlace")

        resultHolder.holdError(command, failFromSecondPlace())

        row = rowOf(command)
        assertThat(row.tryingCount).isEqualTo(2)
        assertThat(row.errorStackTrace).isEqualTo(firstTrace)

        resultHolder.holdError(command, IllegalStateException("another failure"))

        row = rowOf(command)
        assertThat(row.errorMessage).contains("another failure")
        assertThat(row.errorStackTrace).contains("another failure")
        assertThat(row.errorStackTrace).isNotEqualTo(firstTrace)
    }

    private fun failFromFirstPlace(): Exception = RuntimeException("same failure")

    private fun failFromSecondPlace(): Exception = RuntimeException("same failure")
}
