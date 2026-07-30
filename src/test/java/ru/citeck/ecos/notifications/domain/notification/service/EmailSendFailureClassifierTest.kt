package ru.citeck.ecos.notifications.domain.notification.service

import jakarta.mail.Address
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mail.MailSendException
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import java.net.ConnectException
import java.net.SocketTimeoutException

class EmailSendFailureClassifierTest {

    private val classifier = EmailSendFailureClassifier()

    private fun address(email: String = "user@example.com") = InternetAddress(email)

    private fun smtpAddressFailed(code: Int, err: String = "smtp error"): SMTPAddressFailedException {
        return SMTPAddressFailedException(address(), "RCPT TO", code, err)
    }

    @Test
    fun `invalid addresses without valid unsent is permanent`() {
        val ex = SendFailedException(
            "Invalid Addresses",
            null,
            emptyArray<Address>(),
            emptyArray<Address>(),
            arrayOf<Address>(address("broken@nowhere"))
        )

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `invalid addresses with remaining valid unsent is transient`() {
        // some recipients are still deliverable, a retry can reach them
        val ex = SendFailedException(
            "Partial failure",
            smtpAddressFailed(421, "service not available"),
            emptyArray<Address>(),
            arrayOf<Address>(address("ok@example.com")),
            arrayOf<Address>(address("broken@nowhere"))
        )

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `smtp 550 is permanent`() {
        assertThat(classifier.classify(smtpAddressFailed(550, "mailbox unavailable")))
            .isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `smtp 421 is transient`() {
        assertThat(classifier.classify(smtpAddressFailed(421, "service not available")))
            .isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `smtp 451 is transient`() {
        assertThat(classifier.classify(smtpAddressFailed(451, "local error in processing")))
            .isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `smtp 552 mailbox full is transient`() {
        assertThat(classifier.classify(smtpAddressFailed(552, "exceeded storage allocation")))
            .isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `smtp 5xx quota related is transient`() {
        assertThat(classifier.classify(smtpAddressFailed(554, "recipient over quota")))
            .isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `connect exception is transient`() {
        val ex = MessagingException("Couldn't connect to host", ConnectException("Connection refused"))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `socket timeout is transient`() {
        val ex = MessagingException("Read timed out", SocketTimeoutException("Read timed out"))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `malformed address is permanent`() {
        // MimeMessageHelper.setTo parses addresses before any SMTP dialogue
        val ex = assertThrows<AddressException> { InternetAddress.parse("john doe@example", true) }

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `malformed address deep in cause chain is permanent`() {
        val ex = RuntimeException("send failed", AddressException("Illegal address", "john doe@example"))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `unparsable smtp code falls through to the invalid address rule`() {
        // angus reports -1 when the server reply line could not be parsed: the return code
        // decides nothing, but the invalid-address list still does
        val ex = SMTPSendFailedException(
            "RCPT TO",
            -1,
            "unparsable reply",
            null,
            emptyArray<Address>(),
            emptyArray<Address>(),
            arrayOf<Address>(address("broken@nowhere"))
        )

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `mixed permanent and temporary address rejections are transient`() {
        // javamail chains one exception per rejected recipient: 550 for the first, 450 for the
        // second — a permanent verdict here would drop the greylisted recipient's mail forever
        val chain = smtpAddressFailed(550, "mailbox unavailable")
        chain.setNextException(smtpAddressFailed(450, "try again later"))
        val ex = MailSendException(mapOf<Any, Exception>("msg" to chain))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `5xx command failure with remaining valid unsent is transient`() {
        val ex = SMTPSendFailedException(
            "RCPT TO",
            550,
            "mailbox unavailable",
            null,
            emptyArray<Address>(),
            arrayOf<Address>(address("deferred@example.com")),
            arrayOf<Address>(address("broken@nowhere"))
        )

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `unknown exception is not classified`() {
        assertThat(classifier.classify(RuntimeException("boom"))).isNull()
    }

    @Test
    fun `smtp code inside spring MailSendException is found`() {
        // spring collects per-message failures in messageExceptions and leaves the cause empty
        val ex = MailSendException(mapOf<Any, Exception>("msg" to smtpAddressFailed(550)))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `smtp code deep in cause chain is found`() {
        val ex = RuntimeException("send failed", MessagingException("wrapper", smtpAddressFailed(550)))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }
}
