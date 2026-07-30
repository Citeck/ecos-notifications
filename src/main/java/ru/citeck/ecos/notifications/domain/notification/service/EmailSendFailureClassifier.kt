package ru.citeck.ecos.notifications.domain.notification.service

import jakarta.mail.SendFailedException
import jakarta.mail.internet.AddressException
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException
import org.eclipse.angus.mail.util.MailConnectException
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Classifies SMTP/JavaMail send failures.
 *
 * Rules:
 * - [SendFailedException] still holding valid-unsent addresses — those recipients were not
 *   rejected outright and a retry can still reach them -> TRANSIENT
 * - [SendFailedException] with invalid addresses only (no valid-unsent left) — the send
 *   can never succeed for this recipient list -> PERMANENT
 * - [AddressException] — a malformed address rejected before the SMTP dialogue -> PERMANENT
 * - SMTP 5xx reply -> PERMANENT, except 552 and quota-related replies which usually mean
 *   a full mailbox that may be cleaned up -> TRANSIENT
 * - SMTP 4xx reply, connection failures, timeouts -> TRANSIENT
 * - anything else -> null (unknown, caller decides)
 *
 * The whole exception chain is inspected and TRANSIENT wins over PERMANENT: a message to a
 * mix of permanently and temporarily rejected recipients chains one exception per address, and
 * a permanent verdict there would drop the mail of the temporarily rejected ones forever.
 */
@Component
class EmailSendFailureClassifier : SendFailureClassifier {

    override fun classify(e: Throwable): FailureKind? {
        var permanent = false
        for (element in sendFailureExceptionChain(e)) {
            when (classifyElement(element)) {
                FailureKind.TRANSIENT -> return FailureKind.TRANSIENT
                FailureKind.PERMANENT -> permanent = true
                null -> {
                    // unrecognized element, keep walking
                }
            }
        }
        return if (permanent) FailureKind.PERMANENT else null
    }

    private fun classifyElement(e: Throwable): FailureKind? {
        // checked before the reply code: a per-command 5xx (SMTPSendFailedException is a
        // SendFailedException too) must not condemn the recipients that are still deliverable
        if (e is SendFailedException && !e.validUnsentAddresses.isNullOrEmpty()) {
            return FailureKind.TRANSIENT
        }
        val smtpReturnCode = when (e) {
            is SMTPAddressFailedException -> e.returnCode
            is SMTPSendFailedException -> e.returnCode
            is SMTPSenderFailedException -> e.returnCode
            else -> null
        }
        if (smtpReturnCode != null) {
            // an unparsable reply code yields null - fall through instead of returning, the
            // exception may still be a SendFailedException carrying invalid addresses
            classifyReturnCode(smtpReturnCode, e.message)?.let { return it }
        }
        // a malformed address is rejected by JavaMail before any SMTP dialogue and can never
        // become parsable on a retry
        if (e is AddressException) {
            return FailureKind.PERMANENT
        }
        if (e is SendFailedException) {
            if (!e.invalidAddresses.isNullOrEmpty()) {
                return FailureKind.PERMANENT
            }
            // undetermined failure: keep walking the chain, nextException may hold SMTP codes
            return null
        }
        if (e is MailConnectException ||
            e is SocketException ||
            e is SocketTimeoutException ||
            e is UnknownHostException
        ) {
            return FailureKind.TRANSIENT
        }
        return null
    }

    private fun classifyReturnCode(code: Int, message: String?): FailureKind? {
        return when {
            code in 400..499 -> FailureKind.TRANSIENT
            code == MAILBOX_FULL_CODE -> FailureKind.TRANSIENT
            code in 500..599 && message?.contains("quota", ignoreCase = true) == true -> FailureKind.TRANSIENT
            code in 500..599 -> FailureKind.PERMANENT
            else -> null
        }
    }

    companion object {
        // 552 "exceeded storage allocation" — permanent by RFC class, but in practice
        // means a full mailbox that may be cleaned up, so retrying makes sense
        private const val MAILBOX_FULL_CODE = 552
    }
}
