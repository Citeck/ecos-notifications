package ru.citeck.ecos.notifications.service.providers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailNotificationProvider(@field:Autowired private val emailSender: JavaMailSender) {

    fun sendTest() {
        val message = SimpleMailMessage()
        message.setFrom("noreply@baeldung.com")
        message.setTo("romanchabest55@gmail.com")
        message.setSubject("its test message")
        message.setText("Some body there")

        emailSender.send(message)
    }

}
