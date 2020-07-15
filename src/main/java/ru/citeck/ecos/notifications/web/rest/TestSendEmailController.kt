package ru.citeck.ecos.notifications.web.rest

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.citeck.ecos.notifications.domain.notification.Notification
import ru.citeck.ecos.notifications.domain.notification.NotificationType
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.service.NotificationService
import java.util.*

@RestController
@RequestMapping("/test/email")
class TestSendEmailController(
    private val notificationService: NotificationService,
    private val templateService: NotificationTemplateService
) {


    @GetMapping
    fun send() {

        val model = mutableMapOf<String, Any>()
        model["name"] = "Roman"
        model["lastName"] = "Makarskiy"
        model["age"] = "999"

        val template = templateService.findById("inner-lang-template")

        val notification = Notification(
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = listOf("roman.makarskiy@citeck.ru"),
            template = template.get(),
            model = model,
            from = "test@mail.ru"
        )

        notificationService.send(notification)
    }

}
