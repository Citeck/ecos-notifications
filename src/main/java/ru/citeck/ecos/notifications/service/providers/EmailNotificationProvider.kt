package ru.citeck.ecos.notifications.service.providers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.NotificationType
import java.nio.charset.StandardCharsets

private const val CONTENT_TYPE = "text/html; charset=UTF-8"

@Service
class EmailNotificationProvider(@field:Autowired private val emailSender: JavaMailSender) : NotificationProvider {

    override fun getType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun send(title: String, body: String, to: List<String>, from: String) {
        val msg = emailSender.createMimeMessage()

        val helper = MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name())

        msg.setContent(body, CONTENT_TYPE)
        helper.setTo(to.toTypedArray())
        helper.setSubject(title)

        emailSender.send(msg)
    }

}
