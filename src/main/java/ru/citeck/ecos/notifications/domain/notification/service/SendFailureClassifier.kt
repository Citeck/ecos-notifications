package ru.citeck.ecos.notifications.domain.notification.service

import jakarta.mail.MessagingException
import org.springframework.mail.MailSendException
import ru.citeck.ecos.notifications.domain.notification.FailureKind

/**
 * Classifies a send failure for one delivery channel (email, firebase, ...).
 *
 * Implementations are Spring beans picked up by [NotificationFailureClassifier].
 *
 * @return the failure kind, or null when the error is not recognized by this classifier.
 * The caller defaults unknown errors to [FailureKind.TRANSIENT]: a false PERMANENT verdict
 * loses the notification forever, a false TRANSIENT only costs a few cheap retry attempts.
 */
interface SendFailureClassifier {

    fun classify(e: Throwable): FailureKind?
}

/**
 * Flattens an exception into the full chain to inspect: follows [Throwable.cause],
 * [MessagingException.getNextException] (JavaMail chains errors through the latter) and
 * [MailSendException.getMessageExceptions] (Spring collects per-message failures there and
 * leaves the cause empty), breadth-first, cycle-safe.
 */
internal fun sendFailureExceptionChain(root: Throwable): List<Throwable> {
    val chain = ArrayList<Throwable>()
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    val queue = ArrayDeque<Throwable>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!visited.add(current)) {
            continue
        }
        chain.add(current)
        current.cause?.let { queue.add(it) }
        if (current is MessagingException) {
            current.nextException?.let { queue.add(it) }
        }
        if (current is MailSendException) {
            current.messageExceptions?.forEach { queue.add(it) }
        }
    }
    return chain
}
