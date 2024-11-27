package ru.citeck.ecos.notifications.domain.reminder

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.apache.commons.mail2.jakarta.util.MimeMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.AwaitingNotificationDispatcher
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_SOURCE_ID
import ru.citeck.ecos.notifications.domain.reminder.dto.Reminder
import ru.citeck.ecos.notifications.domain.reminder.service.CertificateRemindDateHelper
import ru.citeck.ecos.notifications.stringFromResource
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.secrets.lib.provider.InMemSecretsProvider
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class ReminderNotificationsMetaTest {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var awaitNotificationDispatcher: AwaitingNotificationDispatcher

    @MockBean
    private lateinit var certificateRemindDateHelper: CertificateRemindDateHelper

    private lateinit var greenMail: GreenMail

    private var expirationReminderRef: EntityRef? = null

    companion object {
        private val userIvanRef = "emodel/person@ivan.petrov".toEntityRef()
        private val ivanRecord = IvanRecord()

        private val certificateRef = "emodel/secret@cert-reminder-notification-test".toEntityRef()
        private val certificateRecord = CertificateRecord()
    }

    @BeforeEach
    fun setUp() {
        localAppService.deployLocalArtifacts()

        notificationRepository.deleteAll()
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        recordsService.register(
            RecordsDaoBuilder.create("emodel/person")
                .addRecord(
                    userIvanRef.getLocalId(),
                    ivanRecord
                )
                .build()
        )

        recordsService.register(
            RecordsDaoBuilder.create("emodel/secret")
                .addRecord(
                    certificateRef.getLocalId(),
                    certificateRecord
                )
                .build()
        )

        val privateKey = ResourceUtils.getFile("classpath:mail-test-certs/private_key_junit.pem").readText()
        val certString = ResourceUtils.getFile("classpath:mail-test-certs/certificate_junit.pem").readText()
        InMemSecretsProvider.registerCertificate("cert-reminder-notification-test", privateKey, certString)

        val reminderData = Json.mapper.convert(
            stringFromResource("reminder/cert-expiration-reminder-notification.yml"),
            Reminder::class.java
        )!!
        expirationReminderRef = recordsService.mutate(EntityRef.create(REMINDER_SOURCE_ID, ""), reminderData)
    }

    @AfterEach
    fun tearDown() {
        greenMail.stop()
        notificationRepository.deleteAll()

        expirationReminderRef?.let {
            recordsService.delete(it)
        }
    }

    @Test
    fun `certificate expiration notification default template meta test`() {
        Mockito.`when`(certificateRemindDateHelper.getRemindDate(any(), any())).thenReturn(
            Instant.now().minus(5, ChronoUnit.MINUTES)
        )
        Mockito.`when`(certificateRemindDateHelper.isBeforeNow(any())).thenReturn(false)

        awaitNotificationDispatcher.dispatchNotifications()

        val emails = greenMail.receivedMessages

        assertThat(emails).hasSize(1)

        assertThat(emails[0].allRecipients.size).isEqualTo(1)
        assertThat(emails[0].allRecipients[0].toString()).isEqualTo(ivanRecord.email)

        assertThat(emails[0].subject).isEqualTo("Уведомление о сроке действия сертификата")

        val body = MimeMessageParser(emails[0]).parse().htmlContent.trim()
        assertThat(body).isEqualTo(
            "Приближается окончание срока действия сертификата " +
                "<a href=\"http://localhost/v2/dashboard?recordRef=emodel/secret@cert-reminder-notification-test\" " +
                "target=\"_blank\">Test certificate</a>, действительного до 16.01.3005, 11:08."
        )
    }

    private class IvanRecord(

        @AttName("email")
        val email: String = "ivan@mail.com"

    )

    private class CertificateRecord(

        @AttName("name")
        val name: MLText = MLText("Test certificate"),

        @AttName("certValidityTo")
        val certValidityTo: Instant = Instant.parse("3005-01-16T08:08:46Z")

    )
}
