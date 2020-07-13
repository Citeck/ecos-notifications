package ru.citeck.ecos.notifications.service.providers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationProvider {

    @Autowired
    private final JavaMailSender emailSender;

    public EmailNotificationProvider(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void sendTest() {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@baeldung.com");
        message.setTo("romanchabest55@gmail.com");
        message.setSubject("its test message");
        message.setText("Some body there");

        emailSender.send(message);

    }

}
