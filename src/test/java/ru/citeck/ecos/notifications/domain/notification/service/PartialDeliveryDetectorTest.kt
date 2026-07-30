package ru.citeck.ecos.notifications.domain.notification.service

import jakarta.mail.Address
import jakarta.mail.SendFailedException
import jakarta.mail.internet.InternetAddress
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.junit.jupiter.api.Test
import org.springframework.mail.MailSendException

class PartialDeliveryDetectorTest {

    private fun address(email: String) = InternetAddress(email)

    private fun sendFailed(
        sent: Array<Address> = emptyArray(),
        unsent: Array<Address> = emptyArray(),
        invalid: Array<Address> = emptyArray()
    ): SendFailedException {
        return SendFailedException(
            "Invalid Addresses",
            SMTPAddressFailedException(address("broken@nowhere"), "RCPT TO", 550, "mailbox unavailable"),
            sent,
            unsent,
            invalid
        )
    }

    @Test
    fun `failure with sent addresses is a partial delivery`() {
        val ex = sendFailed(
            sent = arrayOf(address("ok@example.com")),
            invalid = arrayOf(address("broken@nowhere"))
        )

        val partial = PartialDeliveryDetector.detect(ex)

        assertThat(partial).isNotNull
        assertThat(partial!!.sent).containsExactly("ok@example.com")
        assertThat(partial.rejected).containsExactly("broken@nowhere")
        assertThat(partial.asNote()).isEqualTo(
            "Partially delivered to: ok@example.com. Rejected: broken@nowhere"
        )
    }

    @Test
    fun `unsent addresses are reported as rejected too`() {
        val ex = sendFailed(
            sent = arrayOf(address("ok@example.com")),
            unsent = arrayOf(address("skipped@example.com")),
            invalid = arrayOf(address("broken@nowhere"))
        )

        val partial = PartialDeliveryDetector.detect(ex)

        assertThat(partial!!.rejected).containsExactly("broken@nowhere", "skipped@example.com")
    }

    @Test
    fun `failure wrapped by spring MailSendException is detected`() {
        val ex = MailSendException(
            mapOf<Any, Exception>(
                "msg" to sendFailed(
                    sent = arrayOf(address("ok@example.com")),
                    invalid = arrayOf(address("broken@nowhere"))
                )
            )
        )

        assertThat(PartialDeliveryDetector.detect(ex)).isNotNull
    }

    @Test
    fun `failure without delivered recipients is not a partial delivery`() {
        val ex = MailSendException(
            mapOf<Any, Exception>("msg" to sendFailed(invalid = arrayOf(address("broken@nowhere"))))
        )

        assertThat(PartialDeliveryDetector.detect(ex)).isNull()
    }

    @Test
    fun `unrelated failure is not a partial delivery`() {
        assertThat(PartialDeliveryDetector.detect(RuntimeException("boom"))).isNull()
    }
}
