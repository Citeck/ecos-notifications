package ru.citeck.ecos.notifications

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.api.commands.SendNotificationCommandExecutor
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.RecipientsSendStrategy
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class RecipientsSendStrategyCommandTest : BaseMailTest() {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    private var logAppender: ListAppender<ILoggingEvent>? = null
    private var executorLogger: Logger? = null

    companion object {
        private val TEMPLATE_REF = EntityRef.create("notifications", "template", "test-template")
        private val RECIPIENTS = setOf(
            "recipient-1@gmail.com",
            "recipient-2@gmail.com",
            "recipient-3@gmail.com"
        )
    }

    @AfterEach
    fun detachLogAppender() {
        logAppender?.let { appender ->
            executorLogger?.detachAppender(appender)
            appender.stop()
        }
        logAppender = null
        executorLogger = null
    }

    private fun buildCommand(
        recipients: Set<String>,
        strategy: RecipientsSendStrategy,
        cc: Set<String> = emptySet(),
        bcc: Set<String> = emptySet()
    ): SendNotificationCommand {
        return SendNotificationCommand(
            id = UUID.randomUUID().toString(),
            record = EntityRef.EMPTY,
            templateRef = TEMPLATE_REF,
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = recipients,
            cc = cc,
            bcc = bcc,
            model = templateModel,
            from = "testFrom@mail.ru",
            recipientsSendStrategy = strategy
        )
    }

    private fun execute(command: SendNotificationCommand): SendNotificationResult {
        return commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)!!
    }

    private fun startCapturingExecutorLogs() {
        executorLogger = LoggerFactory.getLogger(SendNotificationCommandExecutor::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().also {
            it.start()
            executorLogger!!.addAppender(it)
        }
    }

    @Test
    fun perRecipientStrategyShouldSplitIntoSeparateNotifications() {
        val result = execute(buildCommand(RECIPIENTS, RecipientsSendStrategy.PER_RECIPIENT))

        assertThat(result.status).isEqualTo(NotificationResultStatus.OK.value)

        // One notification record per recipient, each with its own extId.
        val notifications = notificationRepository.findAll()
        assertThat(notifications).hasSize(RECIPIENTS.size)
        assertThat(notifications.map { it.extId }.toSet()).hasSize(RECIPIENTS.size)
        notifications.forEach { assertThat(it.state).isEqualTo(NotificationState.SENT) }

        // Each persisted sub-command carries exactly one recipient, and together they cover all of them.
        val recipientsPerNotification = notifications.map { entity ->
            val command = Json.mapper.read(entity.data, SendNotificationCommand::class.java)!!
            assertThat(command.recipients).hasSize(1)
            command.recipients.first()
        }
        assertThat(recipientsPerNotification.toSet()).isEqualTo(RECIPIENTS)

        // Each delivered email goes to a single To address (recipients don't see each other).
        val emails = greenMail.receivedMessages
        assertThat(emails).hasSize(RECIPIENTS.size)
        emails.forEach { assertThat(it.allRecipients).hasSize(1) }
        assertThat(emails.map { it.allRecipients[0].toString() }.toSet()).isEqualTo(RECIPIENTS)
    }

    @Test
    fun combinedStrategyShouldSendSingleNotificationToAllRecipients() {
        val result = execute(buildCommand(RECIPIENTS, RecipientsSendStrategy.COMBINED))

        assertThat(result.status).isEqualTo(NotificationResultStatus.OK.value)

        // Default behavior is unchanged: a single notification record for the whole command.
        val notifications = notificationRepository.findAll()
        assertThat(notifications).hasSize(1)

        val command = Json.mapper.read(notifications[0].data, SendNotificationCommand::class.java)!!
        assertThat(command.recipients).isEqualTo(RECIPIENTS)

        // A single email addressed to all recipients. GreenMail delivers one copy per RCPT TO, so a
        // combined send still yields one message per recipient - but every message lists the full To.
        val emails = greenMail.receivedMessages
        assertThat(emails).hasSize(RECIPIENTS.size)
        emails.forEach { message ->
            assertThat(message.allRecipients.map { it.toString() }.toSet()).isEqualTo(RECIPIENTS)
        }
    }

    @Test
    fun perRecipientStrategyWithCcShouldNotSplitAndWarn() {
        startCapturingExecutorLogs()

        val result = execute(
            buildCommand(
                recipients = RECIPIENTS,
                strategy = RecipientsSendStrategy.PER_RECIPIENT,
                cc = setOf("cc-recipient@gmail.com")
            )
        )

        assertThat(result.status).isEqualTo(NotificationResultStatus.OK.value)

        // cc is present, so the strategy is ignored: a single combined notification is sent.
        val notifications = notificationRepository.findAll()
        assertThat(notifications).hasSize(1)

        // The contradiction must be observable as exactly one warning, not silently dropped.
        val warnings = logAppender!!.list.filter { it.level == Level.WARN }
        assertThat(warnings).hasSize(1)
        assertThat(warnings[0].formattedMessage)
            .contains("PER_RECIPIENT")
            .contains("cc/bcc")
    }

    @Test
    fun perRecipientStrategyWithBccShouldNotSplitAndWarn() {
        startCapturingExecutorLogs()

        val result = execute(
            buildCommand(
                recipients = RECIPIENTS,
                strategy = RecipientsSendStrategy.PER_RECIPIENT,
                bcc = setOf("bcc-recipient@gmail.com")
            )
        )

        assertThat(result.status).isEqualTo(NotificationResultStatus.OK.value)

        // bcc is treated the same as cc: the strategy is ignored and a single notification is sent.
        assertThat(notificationRepository.findAll()).hasSize(1)

        val warnings = logAppender!!.list.filter { it.level == Level.WARN }
        assertThat(warnings).hasSize(1)
        assertThat(warnings[0].formattedMessage)
            .contains("PER_RECIPIENT")
            .contains("cc/bcc")
    }

    @Test
    fun perRecipientStrategyWithSingleRecipientProducesOneLinkedNotification() {
        val singleRecipient = setOf("only-recipient@gmail.com")

        val result = execute(buildCommand(singleRecipient, RecipientsSendStrategy.PER_RECIPIENT))

        assertThat(result.status).isEqualTo(NotificationResultStatus.OK.value)

        // A single recipient still goes through the per-recipient branch: one sub-command, one record.
        val notifications = notificationRepository.findAll()
        assertThat(notifications).hasSize(1)

        val command = Json.mapper.read(notifications[0].data, SendNotificationCommand::class.java)!!
        assertThat(command.recipients).isEqualTo(singleRecipient)

        val emails = greenMail.receivedMessages
        assertThat(emails).hasSize(1)
        assertThat(emails[0].allRecipients).hasSize(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(singleRecipient.first())
    }

    @Test
    fun perRecipientStrategyRetriesFailedSubCommandsIndependentlyWithoutDuplicates() {
        // Make every send fail so each sub-command is persisted as its own ERROR record.
        greenMail.stop()

        val parentCommand = buildCommand(RECIPIENTS, RecipientsSendStrategy.PER_RECIPIENT)
        val result = execute(parentCommand)
        assertThat(result.status).isEqualTo(NotificationResultStatus.ERROR.value)

        val failed = notificationRepository.findAllByState(NotificationState.ERROR)
        assertThat(failed).hasSize(RECIPIENTS.size)

        // The split already happened before persistence: each ERROR record holds a single recipient
        // and keeps the link back to the parent command. This is what lets ErrorNotificationRepeater
        // retry each one in isolation.
        failed.forEach { entity ->
            val command = Json.mapper.read(entity.data, SendNotificationCommand::class.java)!!
            assertThat(command.recipients).hasSize(1)
            assertThat(entity.createdFrom).contains(parentCommand.id)
        }

        // Retry: each persisted sub-command is re-executed directly, bypassing splitCommand.
        greenMail.start()
        errorNotificationRepeater.handleErrors()

        assertThat(notificationRepository.findAllByState(NotificationState.ERROR)).isEmpty()
        assertThat(notificationRepository.findAllByState(NotificationState.SENT)).hasSize(RECIPIENTS.size)

        // Exactly one email per recipient - no duplicates of already-known addresses.
        val emails = greenMail.receivedMessages
        assertThat(emails).hasSize(RECIPIENTS.size)
        emails.forEach { assertThat(it.allRecipients).hasSize(1) }
        assertThat(emails.map { it.allRecipients[0].toString() }.toSet()).isEqualTo(RECIPIENTS)
    }
}
