package ru.citeck.ecos.notifications.domain.notification.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.FailureKind

/**
 * Single entry point for failure classification.
 *
 * Order of precedence:
 * 1. [NotificationPermanentException] anywhere in the exception chain -> PERMANENT
 *    (thrown at the failure origin: template resolution, rendering, sender config parsing)
 * 2. First non-null verdict from the registered [SendFailureClassifier] beans
 * 3. Default: TRANSIENT — a false PERMANENT verdict loses the notification forever,
 *    a false TRANSIENT only costs a few cheap retry attempts
 */
@Service
class NotificationFailureClassifier(
    private val classifiers: List<SendFailureClassifier>
) {

    fun classify(e: Throwable): FailureKind {
        if (sendFailureExceptionChain(e).any { it is NotificationPermanentException }) {
            return FailureKind.PERMANENT
        }
        for (classifier in classifiers) {
            classifier.classify(e)?.let { return it }
        }
        return FailureKind.TRANSIENT
    }
}
