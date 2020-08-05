package ru.citeck.ecos.notifications.service.providers

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.lib.NotificationType
import java.nio.charset.StandardCharsets

private const val CONTENT_TYPE = "text/html; charset=UTF-8"

@Service
class EmailNotificationProvider(@field:Autowired private val emailSender: JavaMailSender) : NotificationProvider {

    private val log = KotlinLogging.logger {}

    override fun getType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun send(fitNotification: FitNotification) {
        log.debug("Send email notification: $fitNotification")

        val msg = emailSender.createMimeMessage()

        val helper = MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name())

        msg.setContent(fitNotification.body, CONTENT_TYPE)
        helper.setTo(fitNotification.recipients.toTypedArray())
        fitNotification.title?.let { helper.setSubject(it) }

        helper.setFrom(fitNotification.from)
        helper.setCc(fitNotification.cc.toTypedArray())
        helper.setBcc(fitNotification.bcc.toTypedArray())

        emailSender.send(msg)
    }

}
