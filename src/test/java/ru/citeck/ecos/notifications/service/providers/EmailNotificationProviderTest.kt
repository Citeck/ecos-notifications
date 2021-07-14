package ru.citeck.ecos.notifications.service.providers

import com.sun.istack.internal.ByteArrayDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.springframework.mail.javamail.JavaMailSender
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import java.util.*
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class EmailNotificationProviderTest {

    private lateinit var notificationProvider: EmailNotificationProvider
    private val mailSenderMock: JavaMailSender = Mockito.mock(JavaMailSender::class.java)
    private val properties = ApplicationProperties()

    private val emails = mutableListOf<MimeMessage>()

    init {
        Mockito.`when`(mailSenderMock.send(Mockito.any(MimeMessage::class.java))).then { invocation ->
            emails.add(invocation.getArgument(0))
        }
        Mockito.`when`(mailSenderMock.createMimeMessage()).thenAnswer {
            MimeMessage(Session.getInstance(Properties()))
        }
    }

    private fun initProvider(emailProps: ApplicationProperties.Email?) {
        properties.email.setDataFromOther(emailProps)
        emails.clear()
        notificationProvider = EmailNotificationProvider(mailSenderMock, properties)
    }

    @Test
    fun testWithDefaultProps() {

        val props = ApplicationProperties.Email()
        initProvider(props)

        val notification = FitNotification(
            "body",
            "title",
            setOf("recepient0", "recepient1"),
            "from@email.ru",
            setOf("copy-to-0@email.ru", "copy-to-1@email.ru"),
            setOf("bcc-copy-to-0@email.ru", "bcc-copy-to-1@email.ru")
        )
        notificationProvider.send(notification)
        validateMessage(notification)
    }

    @Test
    fun testWithFixedFrom() {

        val props = ApplicationProperties.Email()
        props.from.fixed = "fixed-from@email.ru"
        initProvider(props)

        val notification = FitNotification(
            "body",
            "title",
            setOf("recepient0", "recepient1"),
            "from@email.ru",
            setOf("copy-to-0@email.ru", "copy-to-1@email.ru"),
            setOf("bcc-copy-to-0@email.ru", "bcc-copy-to-1@email.ru")
        )

        notificationProvider.send(notification)

        val expectedNotification = notification.copy(from = props.from.fixed)
        validateMessage(expectedNotification)
    }

    @Test
    fun testWithFromMapping() {

        val props = ApplicationProperties.Email()
        props.from.setMapping(mapOf(
            "from-mapping-key0@email.ru" to "from-mapping-value0@email.ru",
            "from-mapping-key1@email.ru" to "from-mapping-value1@email.ru",
            "from-mapping-key2@email.ru" to "from-mapping-value2@email.ru"
        ).map {
            ApplicationProperties.EmailMapping(it.key, it.value)
        }.toList())

        initProvider(props)

        val notification = FitNotification(
            "body",
            "title",
            setOf("recepient0", "recepient1"),
            "from@email.ru",
            setOf("copy-to-0@email.ru", "copy-to-1@email.ru"),
            setOf("bcc-copy-to-0@email.ru", "bcc-copy-to-1@email.ru")
        )

        notificationProvider.send(notification)
        validateMessage(notification)

        props.from.mapping.forEach {

            emails.clear()

            val notificationWithMappingKey = notification.copy(from = it.key)
            val notificationWithMappingValue = notification.copy(from = it.value)

            notificationProvider.send(notificationWithMappingKey)
            validateMessage(notificationWithMappingValue)
        }
    }

    @Test
    fun testWithAttachments() {
        val props = ApplicationProperties.Email()
        initProvider(props)

        val dataSource = ByteArrayDataSource("test".toByteArray(), "type")

        val notification = FitNotification(
            "body",
            "title",
            setOf("recepient0", "recepient1"),
            "from@email.ru",
            setOf("copy-to-0@email.ru", "copy-to-1@email.ru"),
            setOf("bcc-copy-to-0@email.ru", "bcc-copy-to-1@email.ru"),
            mapOf("fileName.pdf" to dataSource)
        )
        notificationProvider.send(notification)
        validateMessage(notification)
    }

    private fun validateMessage(notification: FitNotification) {

        assertThat(emails).hasSize(1)
        assertThat(emails[0].from).hasSize(1)
        assertThat((emails[0].from[0] as InternetAddress).address).isEqualTo(notification.from)
        if (notification.attachments.isNotEmpty()){
            assertThat(emails[0].content is MimeMultipart).isTrue()
        } else {
            assertThat(emails[0].content).isEqualTo(notification.body)
        }
        assertThat(emails[0].subject).isEqualTo(notification.title)

        assertThat(emails[0].getRecipients(Message.RecipientType.TO).map {
            (it as InternetAddress).address
        }).containsExactlyInAnyOrder(*notification.recipients.toTypedArray())

        assertThat(emails[0].getRecipients(Message.RecipientType.CC).map {
            (it as InternetAddress).address
        }).containsExactlyInAnyOrder(*notification.cc.toTypedArray())

        assertThat(emails[0].getRecipients(Message.RecipientType.BCC).map {
            (it as InternetAddress).address
        }).containsExactlyInAnyOrder(*notification.bcc.toTypedArray())
    }
}
