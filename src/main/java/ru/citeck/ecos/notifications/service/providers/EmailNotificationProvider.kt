package ru.citeck.ecos.notifications.service.providers

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeUtility
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.sender.NotificationSender
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationType
import java.nio.charset.StandardCharsets

@Component
class EmailNotificationProvider(
    private val emailSender: JavaMailSender,
    properties: ApplicationProperties
) : NotificationProvider, NotificationSender<Unit> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val emailProps = initEmailProps(properties)

    override fun getNotificationType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun getSenderType(): String {
        return "default"
    }

    override fun sendNotification(notification: FitNotification, config: Unit): NotificationSenderSendStatus {
        send(notification)
        return NotificationSenderSendStatus.SENT
    }

    override fun getConfigClass(): Class<Unit> {
        return Unit::class.java
    }

    override fun getType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun send(fitNotification: FitNotification) {
        log.debug { "Send email notification: $fitNotification" }

        val msg = emailSender.createMimeMessage()

        val isMultipartMessageType = fitNotification.attachments.isNotEmpty()

        val helper = MimeMessageHelper(msg, isMultipartMessageType, StandardCharsets.UTF_8.name())

        fitNotification.title?.let { helper.setSubject(it) }
        helper.setText(fitNotification.body, true)

        setFrom(helper, fitNotification)

        helper.setTo(fitNotification.recipients.toTypedArray())
        helper.setCc(fitNotification.cc.toTypedArray())
        helper.setBcc(fitNotification.bcc.toTypedArray())

        for ((key, value) in fitNotification.attachments) {
            log.debug { "Add an attachment '$key' to the MimeMessage as '${MimeUtility.encodeText(key)}'" }
            helper.addAttachment(key, value)
        }

        emailSender.send(msg)
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
