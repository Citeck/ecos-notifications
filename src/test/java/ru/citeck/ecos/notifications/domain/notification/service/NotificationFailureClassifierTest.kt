package ru.citeck.ecos.notifications.domain.notification.service

import jakarta.mail.MessagingException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.notifications.domain.notification.FailureKind

class NotificationFailureClassifierTest {

    private fun delegateReturning(kind: FailureKind?) = object : SendFailureClassifier {
        override fun classify(e: Throwable): FailureKind? = kind
    }

    @Test
    fun `permanent exception in cause chain wins over delegates`() {
        val classifier = NotificationFailureClassifier(listOf(delegateReturning(FailureKind.TRANSIENT)))
        val ex = RuntimeException("wrapper", NotificationPermanentException("template not found"))

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `permanent exception chained via nextException wins`() {
        val ex = MessagingException("wrapper", NotificationPermanentException("broken template"))
        val classifier = NotificationFailureClassifier(emptyList())

        assertThat(classifier.classify(ex)).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `delegate verdict is used`() {
        val classifier = NotificationFailureClassifier(listOf(delegateReturning(FailureKind.PERMANENT)))

        assertThat(classifier.classify(RuntimeException("boom"))).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `first non-null delegate verdict wins`() {
        val classifier = NotificationFailureClassifier(
            listOf(
                delegateReturning(null),
                delegateReturning(FailureKind.PERMANENT),
                delegateReturning(FailureKind.TRANSIENT)
            )
        )

        assertThat(classifier.classify(RuntimeException("boom"))).isEqualTo(FailureKind.PERMANENT)
    }

    @Test
    fun `null from all delegates defaults to transient`() {
        val classifier = NotificationFailureClassifier(listOf(delegateReturning(null)))

        assertThat(classifier.classify(RuntimeException("boom"))).isEqualTo(FailureKind.TRANSIENT)
    }

    @Test
    fun `no delegates defaults to transient`() {
        val classifier = NotificationFailureClassifier(emptyList())

        assertThat(classifier.classify(RuntimeException("boom"))).isEqualTo(FailureKind.TRANSIENT)
    }
}
