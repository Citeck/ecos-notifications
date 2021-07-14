package ru.citeck.ecos.notifications.service.providers

import mu.KotlinLogging
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType
import java.nio.charset.StandardCharsets

@Service
class EmailNotificationProvider(
    private val emailSender: JavaMailSender,
    properties: ApplicationProperties
) : NotificationProvider {

    private val log = KotlinLogging.logger {}

    private val emailProps = initEmailProps(properties)

    override fun getType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun send(fitNotification: FitNotification) {
        log.debug("Send email notification: $fitNotification")

        val msg = emailSender.createMimeMessage()

        val helper = MimeMessageHelper(msg, fitNotification.attachments.isNotEmpty(), StandardCharsets.UTF_8.name())

        helper.setText(fitNotification.body, true)
        helper.setTo(fitNotification.recipients.toTypedArray())
        fitNotification.title?.let { helper.setSubject(it) }
        setFrom(helper, fitNotification)
        helper.setCc(fitNotification.cc.toTypedArray())
        helper.setBcc(fitNotification.bcc.toTypedArray())
        for ((key, value) in fitNotification.attachments) {
            helper.addAttachment(key, value)
        }

        emailSender.send(msg)
    }

    private fun setFrom(msgHelper: MimeMessageHelper, notification: FitNotification) {

        if (emailProps.from.fixed.isNotBlank()) {
            msgHelper.setFrom(emailProps.from.fixed)
        } else {
            if (notification.from.isBlank() && emailProps.from.default.isNotBlank()) {
                msgHelper.setFrom(emailProps.from.default)
            } else {
                val mappedValue = emailProps.from.mapping.getOrDefault(notification.from, notification.from)
                msgHelper.setFrom(mappedValue)
            }
        }
    }

    private fun initEmailProps(appProps: ApplicationProperties) : ApplicationProperties.Email {
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
