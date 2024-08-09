package ru.citeck.ecos.notifications

import org.apache.commons.mail2.jakarta.util.MimeMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.service.AwaitNotificationService
import ru.citeck.ecos.notifications.domain.notification.service.AwaitingNotificationDispatcher
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class AwaitingNotificationDispatcherTest : BaseMailTest() {

    @Autowired
    private lateinit var awaitingNotificationDispatcher: AwaitingNotificationDispatcher

    @Autowired
    private lateinit var awaitNotificationService: AwaitNotificationService

    @Autowired
    private lateinit var notificationDao: NotificationDao

    @Autowired
    private lateinit var recordsService: RecordsService

    companion object {

        private val alfPersonRef = EntityRef.valueOf("alfresco/@workspace://SpacesStore/123-aaa")
        private val templateRef = EntityRef.valueOf(
            "notifications/template@test-awaiting-notification-template"
        )
    }

    @BeforeEach
    fun setUp() {
        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/awaiting/test-awaiting-notification-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!
        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create("alfresco/" + alfPersonRef.getSourceId())
                .addRecord(
                    alfPersonRef.getLocalId(),
                    AlfPersonDocRef()
                )
                .build()
        )
    }

    @Test
    fun `sent pending notification with error should go to status error`() {

        greenMail.stop()

        val notification = Notification.Builder()
            .record(alfPersonRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(listOf("some-email@gmail.com"))
            .lang("ru")
            .build()

        val storedNotification = awaitNotificationService.sendToAwait(notification)

        awaitingNotificationDispatcher.dispatchNotifications()

        val notificationDto = notificationDao.getById(storedNotification.id!!)

        assertThat(notificationDto!!.state).isEqualTo(NotificationState.ERROR)
    }

    @Test
    fun `sent pending notification with expired delay should go to status sent`() {

        val notification = Notification.Builder()
            .record(alfPersonRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(listOf("some-email@gmail.com"))
            .lang("ru")
            .build()

        val fiveMinAgo = Instant.now().minus(5, ChronoUnit.MINUTES)

        val storedNotification = awaitNotificationService.sendToAwait(notification, fiveMinAgo)

        awaitingNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)

        val notificationDto = notificationDao.getById(storedNotification.id!!)

        assertThat(notificationDto!!.state).isEqualTo(NotificationState.SENT)
    }

    @Test
    fun `sent pending notification with not expired delay should stay on awaiting dispatch status`() {

        val notification = Notification.Builder()
            .record(alfPersonRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(listOf("some-email@gmail.com"))
            .lang("ru")
            .build()

        val fiveMinAhead = Instant.now().plus(5, ChronoUnit.MINUTES)

        val storedNotification = awaitNotificationService.sendToAwait(notification, fiveMinAhead)

        awaitingNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(0)

        val notificationDto = notificationDao.getById(storedNotification.id!!)

        assertThat(notificationDto!!.state).isEqualTo(NotificationState.WAIT_FOR_DISPATCH)
    }

    @Test
    fun `sent pending notification should go to status sent`() {

        val notification = Notification.Builder()
            .record(alfPersonRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(listOf("some-email@gmail.com"))
            .lang("ru")
            .build()

        val storedNotification = awaitNotificationService.sendToAwait(notification)

        awaitingNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)

        val notificationDto = notificationDao.getById(storedNotification.id!!)

        assertThat(notificationDto!!.state).isEqualTo(NotificationState.SENT)
    }

    @Test
    fun `awaiting email notification with ru template`() {

        val notification = Notification.Builder()
            .record(alfPersonRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(listOf("some-email@gmail.com"))
            .lang("ru")
            .build()

        awaitNotificationService.sendToAwait(notification)

        awaitingNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Привет Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-email@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Привет Ivan, твоя фамилия Petrenko? Тебе 25 лет?")
    }

    @Test
    fun `awaiting email notification with en template`() {

        val notification = Notification.Builder()
            .record(alfPersonRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(listOf("some-email@gmail.com"))
            .lang("en")
            .build()

        awaitNotificationService.sendToAwait(notification)

        awaitingNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails.size).isEqualTo(1)
        assertThat(emails[0].subject).isEqualTo("Hi Ivan Petrenko")
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo("some-email@gmail.com")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo("Hi Ivan, your last name is Petrenko? You are 25 old?")
    }

    inner class AlfPersonDocRef(

        @AttName("firstName")
        val firstName: String = "Ivan",

        @AttName("lastName")
        val lastName: String = "Petrenko",

        @AttName("age")
        val age: Int = 25

    )
}
