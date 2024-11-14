package ru.citeck.ecos.notifications.service.senders

import mu.KotlinLogging
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import ru.citeck.ecos.ent.notifications.lib.email.sign.EmailCertificateSignConfig
import ru.citeck.ecos.ent.notifications.lib.email.sign.EmailSigner
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderResult
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType
import java.nio.charset.StandardCharsets
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

@Component
class EmailNotificationSender(
    private val emailSender: JavaMailSender,
    private val emailSigner: EmailSigner,
    properties: ApplicationProperties
) : NotificationSender<EmailNotificationSenderConfig> {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val SIGN_RESULT = "signResult"
    }

    private val emailProps = initEmailProps(properties)

    override fun getNotificationType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun getSenderType(): String {
        return NotificationSenderType.DEFAULT.type
    }

    fun sendNotification(notification: FitNotification): NotificationSenderResult {
        return sendNotification(notification, EmailNotificationSenderConfig.EMPTY)
    }

    override fun sendNotification(
        notification: FitNotification,
        config: EmailNotificationSenderConfig
    ): NotificationSenderResult {
        log.debug { "Send email notification: $notification" }

        val msg = emailSender.createMimeMessage()

        val isMultipartMessageType = notification.attachments.isNotEmpty()

        val helper = MimeMessageHelper(msg, isMultipartMessageType, StandardCharsets.UTF_8.name())

        notification.title?.let { helper.setSubject(it) }
        helper.setText(notification.body, true)

        setFrom(helper, notification)

        helper.setTo(notification.recipients.toTypedArray())
        helper.setCc(notification.cc.toTypedArray())
        helper.setBcc(notification.bcc.toTypedArray())

        for ((key, value) in notification.attachments) {
            log.debug { "Add an attachment '$key' to the MimeMessage as '${MimeUtility.encodeText(key)}'" }
            helper.addAttachment(key, value)
        }

        val signResult = emailSigner.signMessageIfRequired(msg, config.certSignConfig)

        emailSender.send(msg)

        return NotificationSenderResult(
            NotificationSenderSendStatus.SENT,
            mapOf(
                SIGN_RESULT to signResult.name
            )
        )
    }

    override fun getConfigClass(): Class<EmailNotificationSenderConfig> {
        return EmailNotificationSenderConfig::class.java
    }

    private fun setFrom(msgHelper: MimeMessageHelper, notification: FitNotification) {
        if (emailProps.from.fixed.isNotBlank()) {
            msgHelper.setFrom(emailProps.from.fixed)
            return
        }

        val normalizedEmail = notification.from.normalizeEmail()
        if (normalizedEmail.isBlank() && emailProps.from.default.isNotBlank()) {
            msgHelper.setFrom(emailProps.from.default)
        } else {
            val mappedValue = emailProps.from.mapping.getOrDefault(normalizedEmail, normalizedEmail)
            msgHelper.setFrom(mappedValue)
        }
    }

    private fun String.normalizeEmail(): String {
        var normEmail = this.trim()
        // remove quotes
        if (normEmail.length >= 2 && normEmail.startsWith('"') && normEmail.endsWith('"')) {
            normEmail = normEmail.substring(1, normEmail.length - 1)
        }

        if (normEmail.isBlank() || normEmail == "null" || normEmail == "undefined") {
            return ""
        }

        return normEmail
    }

    private fun initEmailProps(appProps: ApplicationProperties): ApplicationProperties.Email {
        val email = appProps.email ?: ApplicationProperties.Email()
        email.from = email.from ?: ApplicationProperties.EmailFrom()
        if (email.from.default == null) {
            email.from.default = ""
        }
        if (email.from.fixed == null) {
            email.from.fixed = ""
        }
        return email
    }
}

data class EmailNotificationSenderConfig(
    var certSignConfig: EmailCertificateSignConfig = EmailCertificateSignConfig(),
) {

    companion object {
        val EMPTY = EmailNotificationSenderConfig()
    }
}
