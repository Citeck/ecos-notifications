package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.After
import org.junit.Before
import org.springframework.beans.factory.annotation.Autowired
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService

open class BaseMailTest {

    @Autowired
    protected lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    protected lateinit var notificationsSenderService: NotificationsSenderService

    protected lateinit var greenMail: GreenMail
    protected lateinit var templateModel: MutableMap<String, Any>

    protected lateinit var notificationTemplate: NotificationTemplateWithMeta

    @Before
    fun setupTestContext() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["age"] = "25"

        notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        val defaultSenderDto = Json.mapper.convert(stringJsonFromResource("sender/default_email_sender.json"),
            NotificationsSenderDto::class.java)!!

        notificationsSenderService.save(defaultSenderDto)
    }

    @After
    fun stopMailServer() {
        greenMail.stop()
    }
}
