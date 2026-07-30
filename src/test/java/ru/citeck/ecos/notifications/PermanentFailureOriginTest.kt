package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.notifications.domain.notification.api.commands.UnsafeSendNotificationCommandExecutor
import ru.citeck.ecos.notifications.domain.notification.service.NotificationPermanentException
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*

/**
 * Verifies that unrecoverable failures are marked [NotificationPermanentException]
 * at their origin (template resolution and template rendering), while infrastructure
 * failures stay transient.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class PermanentFailureOriginTest : BaseMailTest() {

    @Autowired
    private lateinit var commandExecutor: UnsafeSendNotificationCommandExecutor

    private fun sendCommand(templateId: String) = SendNotificationCommand(
        id = UUID.randomUUID().toString(),
        record = EntityRef.EMPTY,
        templateRef = EntityRef.create("notifications", "template", templateId),
        type = NotificationType.EMAIL_NOTIFICATION,
        lang = "en",
        recipients = setOf("someUser@gmail.com"),
        model = templateModel,
        from = "testFrom@mail.ru"
    )

    private fun saveTemplate(id: String, body: String) {
        notificationTemplateService.save(
            NotificationTemplateWithMeta(
                id = id,
                name = "Permanent failure test template",
                notificationTitle = MLText("Test title"),
                templateData = mapOf("en" to TemplateDataDto("$id.html_en.ftl", body.toByteArray()))
            )
        )
    }

    private fun exceptionChain(e: Throwable): List<Throwable> {
        return generateSequence(e as Throwable?) { it.cause }.toList()
    }

    @Test
    fun `send with nonexistent template fails with permanent exception in chain`() {
        val thrown = catchThrowable {
            commandExecutor.execute(sendCommand("this-template-does-not-exist"))
        }

        assertThat(thrown).isNotNull()
        assertThat(exceptionChain(thrown)).anyMatch { it is NotificationPermanentException }
    }

    @Test
    fun `send with template referencing missing model attribute fails with permanent exception in chain`() {
        saveTemplate("broken-missing-var-template", "Hello \${thisVarDoesNotExist}")

        val thrown = catchThrowable {
            commandExecutor.execute(sendCommand("broken-missing-var-template"))
        }

        assertThat(thrown).isNotNull()
        assertThat(exceptionChain(thrown)).anyMatch { it is NotificationPermanentException }
    }

    @Test
    fun `send with syntactically broken template fails with permanent exception in chain`() {
        saveTemplate("broken-syntax-template", "Hello \${firstName")

        val thrown = catchThrowable {
            commandExecutor.execute(sendCommand("broken-syntax-template"))
        }

        assertThat(thrown).isNotNull()
        assertThat(exceptionChain(thrown)).anyMatch { it is NotificationPermanentException }
    }

    /**
     * A missing `<#include>`/`<#import>` target is a template defect that surfaces as a
     * TemplateNotFoundException (an IOException), not as a TemplateException — retrying it
     * would burn the whole retry budget on a typo.
     */
    @Test
    fun `send with template including a nonexistent template fails with permanent exception in chain`() {
        saveTemplate("broken-include-template", "Hello <#include \"no-such-template.ftl\">")

        val thrown = catchThrowable {
            commandExecutor.execute(sendCommand("broken-include-template"))
        }

        assertThat(thrown).isNotNull()
        assertThat(exceptionChain(thrown)).anyMatch { it is NotificationPermanentException }
    }

    /**
     * A template is only a defect when FreeMarker itself rejects it. Exceptions thrown by the
     * injected beans reach the caller wrapped in a TemplateException too, but those beans talk to
     * the config service and the database — such a failure is an outage, not a broken template,
     * and must stay retryable (a false-permanent verdict loses the mail forever).
     */
    @Test
    fun `template whose injected bean fails is not marked permanent`() {
        saveTemplate("bean-failure-template", "Logo: \${image.toBase64('no-such-file.png')}")

        val thrown = catchThrowable {
            commandExecutor.execute(sendCommand("bean-failure-template"))
        }

        assertThat(thrown).isNotNull()
        assertThat(exceptionChain(thrown)).noneMatch { it is NotificationPermanentException }
    }

    @Test
    fun `smtp outage is not marked permanent`() {
        greenMail.stop()

        val thrown = catchThrowable {
            commandExecutor.execute(sendCommand("test-template"))
        }

        assertThat(thrown).isNotNull()
        assertThat(exceptionChain(thrown)).noneMatch { it is NotificationPermanentException }
    }
}
