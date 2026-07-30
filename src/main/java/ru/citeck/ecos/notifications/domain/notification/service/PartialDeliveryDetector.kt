package ru.citeck.ecos.notifications.domain.notification.service

import jakarta.mail.SendFailedException

/**
 * Result of a send where the SMTP server accepted the message for part of the recipients
 * and rejected the rest.
 *
 * @param sent addresses the message was accepted for
 * @param rejected addresses the message was not accepted for (invalid + valid-unsent)
 */
data class PartialDelivery(
    val sent: List<String>,
    val rejected: List<String>
) {

    fun asNote(): String {
        return "Partially delivered to: ${sent.joinToString(", ")}. " +
            "Rejected: ${rejected.joinToString(", ")}"
    }
}

/**
 * Detects a partial SMTP acceptance in a failed send.
 *
 * Trade-off: such a send is NOT retried. The message is already delivered to the accepted
 * recipients, and the persisted command holds the whole recipient list, so a retry would
 * re-send to everyone — duplicated mail for people who already got it is worse than a
 * missing delivery for the rejected ones, which is recorded in the error message instead.
 *
 * @return null when the failure is not a partial acceptance (nothing was delivered).
 */
object PartialDeliveryDetector {

    fun detect(e: Throwable): PartialDelivery? {
        for (element in sendFailureExceptionChain(e)) {
            if (element !is SendFailedException) {
                continue
            }
            val sent = element.validSentAddresses.orEmpty().map { it.toString() }
            if (sent.isEmpty()) {
                continue
            }
            val rejected = element.invalidAddresses.orEmpty().map { it.toString() } +
                element.validUnsentAddresses.orEmpty().map { it.toString() }
            return PartialDelivery(sent, rejected)
        }
        return null
    }
}
