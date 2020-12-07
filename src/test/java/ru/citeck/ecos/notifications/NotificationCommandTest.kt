package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.apache.commons.mail.util.MimeMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationCommandTest {

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    private lateinit var greenMail: GreenMail
    private lateinit var templateModel: MutableMap<String, Any>

    @Before
    fun setup() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["age"] = "25"

        val notificationTemplate = Json.mapper.convert(stringJsonFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(notificationTemplate)

        val multiTemplate = Json.mapper.convert(stringJsonFromResource(
            "template/multi-template/test-multi-template.json"), NotificationTemplateWithMeta::class.java)!!
        val multiType1Template = Json.mapper.convert(stringJsonFromResource(
            "template/multi-template/test-type-1-template.json"), NotificationTemplateWithMeta::class.java)!!
        val multiType2Template = Json.mapper.convert(stringJsonFromResource(
            "template/multi-template/test-type-2-template.json"), NotificationTemplateWithMeta::class.java)!!
        val multiType3Template = Json.mapper.convert(stringJsonFromResource(
            "template/multi-template/test-multi-template-with-condition.json"), NotificationTemplateWithMeta::class.java)!!

        notificationTemplateService.save(multiTemplate)
        notificationTemplateService.save(multiType1Template)
        notificationTemplateService.save(multiType2Template)
        notificationTemplateService.save(multiType3Template)
    }

    @Test
    fun sendMultiTemplateNotificationWithoutEcosTypeShouldUseBaseTemplate() {
        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("its base multi template")
    }

    @Test
    fun sendMultiTemplateNotificationWithEcosType1ShouldUseTypedTemplateEn() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["_etype?id"] = "emodel/type@type-1-template"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan! Its type 1 template")
    }

    @Test
    fun sendMultiTemplateNotificationWithEcosType1ShouldUseTypedTemplateRu() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["_etype?id"] = "emodel/type@type-1-template"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "ru",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan! Это шаблон тип 1")
    }

    @Test
    fun sendMultiTemplateNotificationWithEcosType2ShouldUseTypedTemplateEn() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["_etype?id"] = "emodel/type@type-2-template"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan! Its type 2 template")
    }

    @Test
    fun sendMultiTemplateNotificationWithEcosType2ShouldUseTypedTemplateRu() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["_etype?id"] = "emodel/type@type-2-template"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "ru",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan! Это шаблон тип 2")
    }

    @Test
    fun sendEmailNotificationViaCommand() {

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    @Test
    fun sendMultiTemplateLvl2NotificationWithEcosType() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivan"
        templateModel["lastName"] = "Petrenko"
        templateModel["_etype?id"] = "emodel/type@test-type-multi-template-with-condition"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan! Its type 1 template")
    }

    @Test
    fun sendMultiTemplateLvl2NotificationWithEcosType2() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivanushka"
        templateModel["lastName"] = "Vetrenko"
        templateModel["_etype?id"] = "emodel/type@test-type-multi-template-with-condition"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivanushka Vetrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivanushka! Its type 2 template")
    }

    @Test
    fun sendMultiTemplateLvl2NotificationWithEcosType3() {
        templateModel = mutableMapOf()
        templateModel["firstName"] = "Ivanushka"
        templateModel["lastName"] = "Petrenko"
        templateModel["_etype?id"] = "emodel/type@test-type-multi-template-with-condition"

        val command = SendNotificationCommand(
            templateRef = RecordRef.create("notifications", "template", "test-multi-template"),
            type = NotificationType.EMAIL_NOTIFICATION,
            lang = "en",
            recipients = setOf("someUser@gmail.com"),
            model = templateModel,
            from = "testFrom@mail.ru"
        )

        val result = commandsService.executeSync(command, "notifications")
            .getResultAs(SendNotificationResult::class.java)

        assertThat(result!!.status).isEqualTo("ok")

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("someUser@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Its multi template with condition")
    }

    @After
    fun stopMailServer() {
        greenMail.stop()
    }

}
