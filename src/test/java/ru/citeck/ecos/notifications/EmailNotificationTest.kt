package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.mail.util.MimeMessageParser
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
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.notification.service.NotificationService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import java.util.*


@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class EmailNotificationTest {

    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    private lateinit var greenMail: GreenMail
    private lateinit var templateModel: MutableMap<String, Any>

    private lateinit var notificationTemplate: NotificationTemplateWithMeta
    private lateinit var notificationWrongLocaleTemplate: NotificationTemplateWithMeta
    private lateinit var notificationHtmlTemplate: NotificationTemplateWithMeta

    private lateinit var notificationLibMacroTemplate: NotificationTemplateWithMeta
    private lateinit var notificationTestImportTemplate: NotificationTemplateWithMeta

    private lateinit var notificationLibIncludeTemplate: NotificationTemplateWithMeta
    private lateinit var notificationTestIncludeTemplate: NotificationTemplateWithMeta

    private lateinit var notificationTestBeansTemplate: NotificationTemplateWithMeta

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
            "template/test-template-wrong-locale-test.json"), NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(notificationTemplate)
        notificationTemplateService.save(notificationHtmlTemplate)
        notificationTemplateService.save(notificationWrongLocaleTemplate)

        notificationLibMacroTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/lib-macro-template.json"), NotificationTemplateWithMeta::class.java)!!
        notificationTestImportTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/test-import-template.json"), NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(notificationLibMacroTemplate)
        notificationTemplateService.save(notificationTestImportTemplate)

        notificationLibIncludeTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/lib-include-template.json"), NotificationTemplateWithMeta::class.java)!!
        notificationTestIncludeTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/test-include-template.json"), NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(notificationLibIncludeTemplate)
        notificationTemplateService.save(notificationTestIncludeTemplate)

        notificationTestBeansTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/test-beans-template.json"), NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(notificationTestBeansTemplate)
    }

    @Test
    fun sendEmailWithImportsTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
            template = notificationTestImportTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualToIgnoringNewLines("user@example.com<br>\n" +
            "\n" +
            "LibDefault: Copyright (C) 1999-2002 Someone. All rights reserved.\n" +
            "<br>\n" +
            "LibEn: Copyright (C) 2000-2002 Someone. All rights reserved.\n" +
            "<br>\n" +
            "LibRu: Copyright (C) 1900-2020 Рога и копыта. Все права защищены.\n" +
            "<br>\n" +
            "libRuShort: Copyright (C) 1900-2020 Рога и копыта. Все права защищены.\n" +
            "<br>")
    }

    @Test
    fun sendEmailWithIncludeTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
            template = notificationTestIncludeTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualToIgnoringNewLines("Шаблон с вложенным шаблоном<br>\n" +
            "По умолчанию - This text should be included. Value from model - Ivan<br>\n" +
            "Английский - This text should be included. Value from model - Ivan<br>\n" +
            "Русский - Этот текст должен быть включен. Значение из модели - Ivan<br>\n" +
            "По умолчанию короткий id - This text should be included. Value from model - Ivan<br>\n" +
            "Английский короткий id - This text should be included. Value from model - Ivan<br>\n" +
            "Русский короткий id- Этот текст должен быть включен. Значение из модели - Ivan")
    }

    @Test
    fun sendTemplatedEnEmailTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
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
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = setOf("some-recipient@gmail.com"),
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
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
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
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = setOf("some-recipient@gmail.com"),
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
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = setOf("some-recipient@gmail.com", "some-recipient-1@gmail.com", "some-recipient2@gmail.com"),
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
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            recipients = setOf("some-recipient@gmail.com"),
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
    fun sendEmailWithNotExistTitleLocaleShouldUseDefaultTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.FRENCH,
            recipients = setOf("some-recipient@gmail.com"),
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
    fun sendEmailWithNotExistBodyLocaleShouldUseExistsLocaleTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
            template = notificationWrongLocaleTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )

        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-recipient@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan, твоя фамилия Petrenko? Тебе 25 лет?")
    }

    @Test
    fun sendEmailWithChangedTemplateTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("some-recipient@gmail.com"),
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

    @Test
    fun emailBeansTest() {
        val notification = RawNotification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("en"),
            recipients = setOf("some-recipient@gmail.com"),
            template = notificationTestBeansTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationService.send(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan. Foo bar")
    }

    @After
    fun stopMailServer() {
        greenMail.stop()
    }

}
