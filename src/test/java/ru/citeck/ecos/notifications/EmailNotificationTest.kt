package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.mail.util.MimeMessageParser
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.Notification
import ru.citeck.ecos.notifications.domain.notification.NotificationType
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.service.NotificationService
import java.util.*


@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class EmailNotificationTest {

    @Autowired
    private lateinit var notificationService: NotificationService

    private lateinit var greenMail: GreenMail
    private lateinit var templateModel: MutableMap<String, Any>

    private lateinit var notificationTemplate: NotificationTemplateWithMeta
    private lateinit var notificationWrongLocaleTemplate: NotificationTemplateWithMeta
    private lateinit var notificationHtmlTemplate: NotificationTemplateWithMeta

    @Before
    fun setup() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["age"] = "25"

        notificationTemplate = Json.mapper.convert(stringJsonFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java)!!
        notificationHtmlTemplate = Json.mapper.convert(stringJsonFromResource("template/test-template-html.json"),
            NotificationTemplateWithMeta::class.java)!!
        notificationWrongLocaleTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/test-template-wring-locale-test.json"), NotificationTemplateWithMeta::class.java)!!
    }

    @Test
    fun sendTemplatedEnEmailTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun sendTemplatedRuEmailTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan, твоя фамилия Petrenko? Тебе 25 лет?")
    }

    @Test
    fun sendTemplatedHtmlEnEmailTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationHtmlTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("<i>Hi Ivan, <b>your</b> last name is Petrenko? You are 25 old?</i>")
    }

    @Test
    fun sendTemplatedHtmlRuEmailTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationHtmlTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("<i>Привет Ivan, <b>твоя</b> фамилия Petrenko? Тебе 25 лет?</i>")
    }

    @Test
    fun sendEmailToMultipleRecipientsTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = listOf("some-recipient@gmail.com", "some-recipient-1@gmail.com", "some-recipient2@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(3)
    }

    @Test
    fun sendEmailWithoutLocaleShouldUseEnLocaleTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun sendEmailWithNotExistTitleLocaleShouldThrowTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.FRENCH,
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )

        val thrown = Assertions.catchThrowable { notificationService.send(notification) }
        assertThat(thrown.message).contains("Notification title not found with locale: fr in template")
    }

    @Test
    fun sendEmailWithNotExistBodyLocaleShouldThrowTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationWrongLocaleTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )

        val thrown = Assertions.catchThrowable { notificationService.send(notification) }
        assertThat(thrown.message).contains("No template with locale <en> found for template")
    }

    @Test
    fun sendEmailWithChangedTemplateTest() {
        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = listOf("some-recipient@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")


        notificationTemplate.notificationTitle = MLText("Its new title for \${firstName}")
        notificationService.send(notification)
        val emails2 = greenMail.receivedMessages

        assertThat(emails2.size).isEqualTo(2)
        assertThat(emails2[1].subject).isEqualTo("Its new title for Ivan")
        assertThat(emails2[1].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body2 = MimeMessageParser(emails2[1]).parse().htmlContent.trim()
        assertThat(body2).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @After
    fun stopMailServer() {
        greenMail.stop()
    }

}
