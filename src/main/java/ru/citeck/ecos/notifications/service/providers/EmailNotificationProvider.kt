package ru.citeck.ecos.notifications.service.providers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.notification.NotificationType

@Service
class EmailNotificationProvider(@field:Autowired private val emailSender: JavaMailSender): NotificationProvider {

    override fun getType(): NotificationType {
        return NotificationType.EMAIL_NOTIFICATION
    }

    override fun send(title: String, body: String, to: List<String>, from: String) {

        val message = SimpleMailMessage()
        message.setFrom(from)
        message.setTo(to[0])
        message.setSubject(title)
        message.setText(body)

        emailSender.send(message)
    }

}
