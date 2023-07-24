package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.notifications.domain.notification.NotificationResultStatus
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.command.SendNotificationCommand
import ru.citeck.ecos.notifications.lib.command.SendNotificationResult
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import java.util.*

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class ResendNotificationTest : BaseMailTest() {

    @Autowired
    protected lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var commandsService: CommandsService

    @Autowired
    private lateinit var recordsService: RecordsService

    private lateinit var initialNotificationRef: RecordRef

    @Before
    fun setUp() {
        notificationRepository.deleteAll()

        initialNotificationRef = let {
            val id = UUID.randomUUID().toString()
            val command = SendNotificationCommand(
                id = id,
                record = RecordRef.create("notifications", "test", "test"),
                templateRef = RecordRef.create("notifications", "template", "test-template"),
                type = NotificationType.EMAIL_NOTIFICATION,
                lang = "en",
                recipients = setOf("someUser@gmail.com"),
                model = templateModel,
                from = "testFrom@mail.ru"
            )
            val result = commandsService.executeSync(command, "notifications")
                .getResultAs(SendNotificationResult::class.java)
            assertThat(result!!.status).isEqualTo(NotificationResultStatus.OK.value)
            RecordRef.create("notification", id)
        }
    }

    @Test
    fun `Resend permissions test`() {
        val ex = assertThrows<Exception> {
            recordsService.mutate(
                initialNotificationRef,
                mapOf("action" to "RESEND")
            )
        }
        assertThat(ex.message).contains("Permission denied")

        AuthContext.runAsFull(SimpleAuthData("admin", listOf(AuthRole.ADMIN))) {
            recordsService.mutate(
                initialNotificationRef,
                mapOf("action" to "RESEND")
            )
        }

        AuthContext.runAsSystem {
            recordsService.mutate(
                initialNotificationRef,
                mapOf("action" to "RESEND")
            )
        }

        val emails = greenMail.receivedMessages
        assertThat(emails.size).isEqualTo(3)
    }

    @Test
    fun `Resend notification`() {
        AuthContext.runAsSystem {
            recordsService.mutate(
                initialNotificationRef,
                mapOf("action" to "RESEND")
            )
        }

        val emails = greenMail.receivedMessages
        assertThat(emails.size).isEqualTo(2)
        assertThat(emails[0].subject).isEqualTo(emails[1].subject)
        assertThat(emails[0].content).isEqualTo(emails[1].content)
        assertThat(emails[0].from).isEqualTo(emails[1].from)
        assertThat(emails[0].allRecipients).isEqualTo(emails[1].allRecipients)
    }
}
