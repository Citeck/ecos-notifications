package ru.citeck.ecos.notifications.web.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.notifications.service.providers.EmailNotificationProvider;

@RestController
@RequestMapping("/test/email")
public class TestSendEmailController {

    private final EmailNotificationProvider notificationProvider;


    public TestSendEmailController(EmailNotificationProvider notificationProvider) {
        this.notificationProvider = notificationProvider;
    }

    @GetMapping
    public void send() {
        notificationProvider.sendTest();
    }

}
