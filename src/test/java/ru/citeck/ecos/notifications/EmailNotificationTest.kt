package ru.citeck.ecos.notifications

import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.mail.util.MimeMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*
import javax.mail.internet.MimeMultipart

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EmailNotificationTest : BaseMailTest() {

    @Autowired
    private lateinit var notificationSender: NotificationSenderService

    private lateinit var notificationWrongLocaleTemplate: NotificationTemplateWithMeta
    private lateinit var notificationHtmlTemplate: NotificationTemplateWithMeta

    private lateinit var notificationLibMacroTemplate: NotificationTemplateWithMeta
    private lateinit var notificationTestImportTemplate: NotificationTemplateWithMeta

    private lateinit var notificationLibIncludeTemplate: NotificationTemplateWithMeta
    private lateinit var notificationTestIncludeTemplate: NotificationTemplateWithMeta

    private lateinit var notificationTestBeansTemplate: NotificationTemplateWithMeta
    private lateinit var rawNotification: RawNotification

    companion object {
        const val DEFAULT_EMAIL_SENDER_ID = "default-email-sender"
        const val RECIPIENT_EMAIL = TestUtils.RECIPIENT_EMAIL
    }

    @BeforeEach
    fun setup() {
        templateModel["process-definition"] = "flowable\$confirm"

        notificationHtmlTemplate = Json.mapper.convert(
            stringJsonFromResource("template/test-template-html.json"),
            NotificationTemplateWithMeta::class.java
        )!!
        notificationWrongLocaleTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/test-template-wrong-locale-test.json"
            ),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationHtmlTemplate)
        notificationTemplateService.save(notificationWrongLocaleTemplate)

        notificationLibMacroTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/lib-macro-template.json"
            ),
            NotificationTemplateWithMeta::class.java
        )!!
        notificationTestImportTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/test-import-template.json"
            ),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationLibMacroTemplate)
        notificationTemplateService.save(notificationTestImportTemplate)

        notificationLibIncludeTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/lib-include-template.json"
            ),
            NotificationTemplateWithMeta::class.java
        )!!
        notificationTestIncludeTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/test-include-template.json"
            ),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationLibIncludeTemplate)
        notificationTemplateService.save(notificationTestIncludeTemplate)

        notificationTestBeansTemplate = Json.mapper.convert(
            stringJsonFromResource(
                "template/test-beans-template.json"
            ),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTestBeansTemplate)

        rawNotification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
    }

    @Test
    fun sendEmailWithImportsTest() {
        notificationSender.sendNotification(rawNotification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualToIgnoringNewLines(
            "user@example.com<br>\n" +
                "\n" +
                "LibDefault: Copyright (C) 1999-2002 Someone. All rights reserved.\n" +
                "<br>\n" +
                "LibEn: Copyright (C) 2000-2002 Someone. All rights reserved.\n" +
                "<br>\n" +
                "LibRu: Copyright (C) 1900-2020 Рога и копыта. Все права защищены.\n" +
                "<br>\n" +
                "libRuShort: Copyright (C) 1900-2020 Рога и копыта. Все права защищены.\n" +
                "<br>"
        )
    }

    @Test
    fun sendEmailWithIncludeTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestIncludeTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualToIgnoringNewLines(
            "Шаблон с вложенным шаблоном<br>\n" +
                "По умолчанию - This text should be included. Value from model - Ivan<br>\n" +
                "Английский - This text should be included. Value from model - Ivan<br>\n" +
                "Русский - Этот текст должен быть включен. Значение из модели - Ivan<br>\n" +
                "По умолчанию короткий id - This text should be included. Value from model - Ivan<br>\n" +
                "Английский короткий id - This text should be included. Value from model - Ivan<br>\n" +
                "Русский короткий id- Этот текст должен быть включен. Значение из модели - Ivan"
        )
    }

    @Test
    fun sendTemplatedEnEmailTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun sendTemplatedRuEmailTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan, твоя фамилия Petrenko? Тебе 25 лет?")
    }

    @Test
    fun sendTemplatedHtmlEnEmailTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationHtmlTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("<i>Hi Ivan, <b>your</b> last name is Petrenko? You are 25 old?</i>")
    }

    @Test
    fun sendTemplatedHtmlRuEmailTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationHtmlTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("<i>Привет Ivan, <b>твоя</b> фамилия Petrenko? Тебе 25 лет?</i>")
    }

    @Test
    fun sendEmailToMultipleRecipientsTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("ru"),
            recipients = setOf(RECIPIENT_EMAIL, "some-recipient-1@gmail.com", "some-recipient2@gmail.com"),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(3)
    }

    @Test
    fun sendEmailWithoutLocaleShouldUseEnLocaleTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun sendEmailWithNotExistTitleLocaleShouldUseDefaultTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.FRENCH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun sendEmailWithNotExistBodyLocaleShouldUseExistsLocaleTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationWrongLocaleTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan, твоя фамилия Petrenko? Тебе 25 лет?")
    }

    @Test
    fun sendEmailWithChangedTemplateTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")

        notificationTemplate.notificationTitle = MLText("Its new title for \${firstName}")
        notificationSender.sendNotification(notification)
        val emails2 = greenMail.receivedMessages

        assertThat(emails2.size).isEqualTo(2)
        assertThat(emails2[1].subject).isEqualTo("Its new title for Ivan")
        assertThat(emails2[1].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val body2 = MimeMessageParser(emails2[1]).parse().htmlContent.trim()
        assertThat(body2).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun emailBeansTest() {
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = LocaleUtils.toLocale("en"),
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestBeansTemplate,
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan. Foo bar")
    }

    @Test
    fun sendEmailWithAttachmentTest() {
        val localModel = templateModel
        val unencodedFileContent = "123"

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "test.txt",
                "originalExt" to "txt",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val content = emails[0].content

        assertThat(content is MimeMultipart).isTrue()
        assertThat((content as MimeMultipart).count).isEqualTo(2)

        var isHaveAttachment = false

        for (i in 0 until content.count) {
            if (content.getBodyPart(i).getHeader("Content-Type")
                .any { it == "text/plain; charset=us-ascii; name=test.txt" }
            ) {
                isHaveAttachment = true
                assertThat(content.getBodyPart(i).content).isEqualTo(unencodedFileContent)
            }
        }

        assertThat(isHaveAttachment).isTrue()
    }

    @Test
    fun sendEmailWithAttachmentsTest() {
        val localModel = templateModel

        localModel["_attachments"] = listOf(
            mapOf(
                "bytes" to "MTIz",
                "previewInfo" to mapOf(
                    "originalName" to "test1.txt",
                    "originalExt" to "txt",
                    "mimetype" to "text/plain"
                )
            ),
            mapOf(
                "bytes" to "MTIz",
                "previewInfo" to mapOf(
                    "originalName" to "test2.pdf",
                    "originalExt" to "pdf",
                    "mimetype" to "application/pdf"
                )
            ),
            mapOf(
                "bytes" to "MTIz",
                "previewInfo" to mapOf(
                    "originalName" to "test3.jpg",
                    "originalExt" to "jpg",
                    "mimetype" to "image/jpeg"
                )
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val content = emails[0].content

        assertThat(content is MimeMultipart).isTrue()
        assertThat((content as MimeMultipart).count).isEqualTo(4)

        var isHaveAttachment1 = false
        var isHaveAttachment2 = false
        var isHaveAttachment3 = false

        for (i in 0 until content.count) {
            if (content.getBodyPart(i).getHeader("Content-Type")
                .any { it == "text/plain; charset=us-ascii; name=test1.txt" }
            ) {
                assertThat(content.getBodyPart(i).content).isNotNull
                isHaveAttachment1 = true
            }
            if (content.getBodyPart(i).getHeader("Content-Type")
                .any { it == "application/pdf; name=test2.pdf" }
            ) {
                assertThat(content.getBodyPart(i).content).isNotNull
                isHaveAttachment2 = true
            }
            if (content.getBodyPart(i).getHeader("Content-Type")
                .any { it == "image/jpeg; name=test3.jpg" }
            ) {
                assertThat(content.getBodyPart(i).content).isNotNull
                isHaveAttachment3 = true
            }
        }

        assertThat(isHaveAttachment1).isTrue()
        assertThat(isHaveAttachment2).isTrue()
        assertThat(isHaveAttachment3).isTrue()
    }

    @Test
    fun sendEmailWithAttachmentWithFileNameWithoutExtTest() {
        val localModel = templateModel
        val unencodedFileContent = "123"

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to "txt",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)

        val content = emails[0].content

        assertThat(content is MimeMultipart).isTrue()
        assertThat((content as MimeMultipart).count).isEqualTo(2)

        var isHaveAttachment = false

        for (i in 0 until content.count) {
            if (content.getBodyPart(i).getHeader("Content-Type")
                .any { it == "text/plain; charset=us-ascii; name=test.txt" }
            ) {
                isHaveAttachment = true
                assertThat(content.getBodyPart(i).content).isEqualTo(unencodedFileContent)
            }
        }

        assertThat(isHaveAttachment).isTrue()
    }

    @Test
    fun sendEmailWithEmptyAttachmentsListTest() {
        val localModel = templateModel

        localModel["_attachments"] = listOf<Map<String, Any>>()

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)
    }

    @Test
    fun sendEmailWithEmptyAttachmentTest() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf<String, Any>()

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(RECIPIENT_EMAIL)
    }

    @Test
    fun sendEmailWithEmptyContentShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "",
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to "txt",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithNullContentShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to null,
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to "txt",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithEmptyNameShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "",
                "originalExt" to "txt",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithNullNameShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to null,
                "originalExt" to "txt",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithEmptyExtShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to "",
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithNullExtShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to null,
                "mimetype" to "text/plain"
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithEmptyMimetypeShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to "txt",
                "mimetype" to ""
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailWithNullMimetypeShouldThrow() {
        val localModel = templateModel

        localModel["_attachments"] = mapOf(
            "bytes" to "MTIz",
            "previewInfo" to mapOf(
                "originalName" to "test",
                "originalExt" to "txt",
                "mimetype" to null
            )
        )

        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            template = notificationTestImportTemplate,
            model = localModel,
            from = "test@mail.ru"
        )

        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun sendEmailThroughSenderWithCondition() {
        notificationsSenderService.delete(DEFAULT_EMAIL_SENDER_ID)
        val defaultSenderDto = Json.mapper.convert(
            stringJsonFromResource("sender/default_email_sender_with_condition.json"),
            NotificationsSenderDto::class.java
        )!!

        notificationsSenderService.save(defaultSenderDto)
        notificationSender.sendNotification(rawNotification)

        val emails = greenMail.receivedMessages
        assertEquals(1, emails.size)
    }

    @Test
    fun sendEmailThroughSenderWithTemplates() {
        notificationsSenderService.delete(DEFAULT_EMAIL_SENDER_ID)
        val defaultSenderDto = Json.mapper.convert(
            stringJsonFromResource("sender/default_email_sender_with_template.json"),
            NotificationsSenderDto::class.java
        )!!

        notificationsSenderService.save(defaultSenderDto)
        notificationSender.sendNotification(rawNotification)

        val emails = greenMail.receivedMessages
        assertEquals(1, emails.size)
    }

    @Test
    fun sendEmailWithoutTemplate() {
        notificationsSenderService.delete(DEFAULT_EMAIL_SENDER_ID)
        val defaultSenderDto = Json.mapper.convert(
            stringJsonFromResource("sender/default_email_sender_with_template.json"),
            NotificationsSenderDto::class.java
        )!!

        notificationsSenderService.save(defaultSenderDto)
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            model = templateModel,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)

        val emails = greenMail.receivedMessages
        assertEquals(1, emails.size)
    }

    @Test
    fun sendEmailWithEmptyModel() {
        notificationsSenderService.delete(DEFAULT_EMAIL_SENDER_ID)
        val defaultSenderDto = Json.mapper.convert(
            stringJsonFromResource("sender/default_email_sender_with_condition.json"),
            NotificationsSenderDto::class.java
        )!!

        notificationsSenderService.save(defaultSenderDto)
        val notification = RawNotification(
            record = RecordRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf(RECIPIENT_EMAIL),
            model = emptyMap(),
            from = "test@mail.ru"
        )
        assertThrows<NotificationException> {
            notificationSender.sendNotification(notification)
        }
    }
}
