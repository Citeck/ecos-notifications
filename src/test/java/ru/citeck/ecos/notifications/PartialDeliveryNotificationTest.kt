package ru.citeck.ecos.notifications

import jakarta.mail.SendFailedException
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.ErrorNotificationRepeater
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*

/**
 * Verifies how SMTP rejections are interpreted, with the transport mocked so it can reply the
 * way a real server would:
 * - partial acceptance (some recipients taken, some rejected) counts as sent - retrying would
 *   re-send to everyone - and the rejected recipients are recorded in the error message;
 * - full rejection with a permanent 5xx reply is FAILED after a single attempt, never retried.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class PartialDeliveryNotificationTest : BaseMailTest() {

    @MockitoBean
    private lateinit var mailSender: JavaMailSender

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var errorNotificationRepeater: ErrorNotificationRepeater

    @BeforeEach
    fun setupMailSender() {
        Mockito.reset(mailSender)
        Mockito.`when`(mailSender.createMimeMessage()).thenAnswer {
            MimeMessage(Session.getInstance(Properties()))
        }
    }

    private fun rejectOnSend(failure: MailSendException) {
        Mockito.doThrow(failure).`when`(mailSender).send(Mockito.any(MimeMessage::class.java))
    }

    /**
     * One recipient accepted, one rejected with a permanent reply.
     */
    private fun partialSendFailure(): MailSendException {
        return sendFailure(validSent = arrayOf(InternetAddress(SENT_RECIPIENT)))
    }

    /**
     * Nothing accepted: the only recipient is rejected with a permanent reply.
     */
    private fun allRecipientsRejected(): MailSendException {
        return sendFailure(validSent = emptyArray())
    }

    private fun sendFailure(validSent: Array<InternetAddress>): MailSendException {
        val rejected = InternetAddress(REJECTED_RECIPIENT)
        val sendFailed = SendFailedException(
            "Invalid Addresses",
            SMTPAddressFailedException(rejected, "RCPT TO", 550, "mailbox unavailable"),
            validSent,
            emptyArray(),
            arrayOf(rejected)
        )
        return MailSendException(mapOf<Any, Exception>("msg" to sendFailed))
    }

    private fun buildCommand() = SendNotificationCommand(
        id = UUID.randomUUID().toString(),
        record = EntityRef.EMPTY,
        templateRef = EntityRef.create("notifications", "template", "test-template"),
        type = NotificationType.EMAIL_NOTIFICATION,
        lang = "en",
        recipients = setOf(SENT_RECIPIENT, REJECTED_RECIPIENT),
        model = templateModel,
        from = "testFrom@mail.ru"
    )

    @Test
    fun `partially delivered notification is sent with a note and is not retried`() {
        rejectOnSend(partialSendFailure())
        val command = buildCommand()

        commandsService.executeSync(command, "notifications")

        val row = notificationRepository.findOneByExtId(command.id).get()
        assertThat(row.state).isEqualTo(NotificationState.SENT)
        assertThat(row.errorMessage).contains(SENT_RECIPIENT)
        assertThat(row.errorMessage).contains("Rejected: $REJECTED_RECIPIENT")
        assertThat(row.nextRetryAt).isNull()
        assertThat(row.failureKind).isNull()
        assertThat(row.tryingCount).isEqualTo(1)

        // nothing is scheduled, so a retry tick has no rows to pick up
        errorNotificationRepeater.handleErrors()

        assertThat(notificationRepository.findOneByExtId(command.id).get().tryingCount).isEqualTo(1)
    }

    /**
     * The invalid-address half of the anti-spam acceptance criterion: a permanent 5xx rejection
     * of every recipient costs exactly one attempt, not 24 hours of retries.
     */
    @Test
    fun `fully rejected notification fails permanently after one attempt`() {
        rejectOnSend(allRecipientsRejected())
        val command = buildCommand()

        commandsService.executeSync(command, "notifications")

        val row = notificationRepository.findOneByExtId(command.id).get()
        assertThat(row.state).isEqualTo(NotificationState.FAILED)
        assertThat(row.failureKind).isEqualTo(FailureKind.PERMANENT)
        assertThat(row.tryingCount).isEqualTo(1)
        assertThat(row.nextRetryAt).isNull()

        repeat(3) { errorNotificationRepeater.handleErrors() }

        val untouched = notificationRepository.findOneByExtId(command.id).get()
        assertThat(untouched.state).isEqualTo(NotificationState.FAILED)
        assertThat(untouched.tryingCount).isEqualTo(1)
        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(MimeMessage::class.java))
    }

    companion object {
        private const val SENT_RECIPIENT = "delivered@mail.ru"
        private const val REJECTED_RECIPIENT = "rejected@mail.ru"
    }
}
