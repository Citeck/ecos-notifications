package ru.citeck.ecos.notifications

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.util.io.Streams
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.license.EcosTestLicense
import ru.citeck.ecos.notifications.domain.notification.RawNotification
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.secrets.lib.provider.InMemSecretsProvider
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class EmailCertSignNotificationTest {

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var notificationSender: NotificationSenderService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var greenMail: GreenMail

    private lateinit var template: NotificationTemplateWithMeta

    private lateinit var certString: String
    private lateinit var certExpiredString: String

    @BeforeEach
    fun setUp() {
        localAppService.deployLocalArtifacts()
        notificationRepository.deleteAll()
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()

        EcosTestLicense.updateContent()
            .set("enterprise", DataValue.TRUE)
            .addFeature("email-certificate-sign")
            .update()

        val privateKey = ResourceUtils.getFile("classpath:mail-test-certs/private_key_junit.pem").readText()
        certString = ResourceUtils.getFile("classpath:mail-test-certs/certificate_junit.pem").readText()
        InMemSecretsProvider.registerCertificate("email-sign-cert", privateKey, certString)

        val privateKeyExpired = ResourceUtils.getFile("classpath:mail-test-certs/private_key_junit_expired.pem")
            .readText()
        certExpiredString = ResourceUtils.getFile("classpath:mail-test-certs/certificate_junit_expired.pem")
            .readText()
        InMemSecretsProvider.registerCertificate("email-sign-expired-cert", privateKeyExpired, certExpiredString)

        template = "template/test-cert-sign-template.json".saveTemplate()
    }

    @AfterEach
    fun tearDown() {
        greenMail.stop()
        notificationRepository.deleteAll()
    }

    @Test
    fun `emails should be signed if feature is enabled in default sender`() {
        sendNotificationWithModel(mapOf("certSign" to true))

        val email = greenMail.receivedMessages[0]
        val content = email.content as MimeMultipart

        assertThat(email.getHeader("Content-Type")[0]).contains("multipart/signed")

        val certFromMail = getCertFromMail(content)
        val certForSign = certString.toX509Certificate()

        assertThat(certFromMail).isEqualTo(certForSign)
    }

    @Test
    fun `server without enterprise license should not allow sign email`() {
        EcosTestLicense.clean()

        val notification = RawNotification(
            record = EntityRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("sign@mail.com"),
            template = template,
            model = mapOf(
                "certSign" to true
            ),
            from = "test@mail.ru"
        )

        assertThrows<IllegalStateException>("Can`t sign email. Enterprise license required.") {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun `server without sign email feature should not allow sign email`() {
        EcosTestLicense.clean()
        EcosTestLicense.updateContent()
            .set("enterprise", DataValue.TRUE)
            .update()

        val notification = RawNotification(
            record = EntityRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("sign@mail.com"),
            template = template,
            model = mapOf(
                "certSign" to true
            ),
            from = "test@mail.ru"
        )

        assertThrows<IllegalStateException>("Can`t sign email. Enterprise license required.") {
            notificationSender.sendNotification(notification)
        }
    }

    @Test
    fun `by default emails should be signed despite the expired certificate`() {
        sendNotificationWithModel(mapOf("certSignExpired" to true))

        val email = greenMail.receivedMessages[0]
        val content = email.content as MimeMultipart

        assertThat(email.getHeader("Content-Type")[0]).contains("multipart/signed")

        val certFromMail = getCertFromMail(content)
        val certForSign = certExpiredString.toX509Certificate()

        assertThat(certFromMail).isEqualTo(certForSign)
    }

    @Test
    fun `email should not sign by cert if skip sign feature is enabled`() {
        sendNotificationWithModel(mapOf("certSignExpiredSkipOn" to true))

        val email = greenMail.receivedMessages[0]

        assertThat(email.getHeader("Content-Type")[0]).doesNotContain("multipart/signed")
    }

    private fun sendNotificationWithModel(model: Map<String, Any>) {
        val notification = RawNotification(
            record = EntityRef.EMPTY,
            type = NotificationType.EMAIL_NOTIFICATION,
            locale = Locale.ENGLISH,
            recipients = setOf("sign@mail.com"),
            template = template,
            model = model,
            from = "test@mail.ru"
        )
        notificationSender.sendNotification(notification)
    }

    private fun getCertFromMail(email: MimeMultipart): X509Certificate {
        val signedContent = (0 until email.count)
            .map { email.getBodyPart(it) as MimeBodyPart }
            .first { it.contentType.contains("application/pkcs7-signature") }
            .content as InputStream

        return JcaX509CertificateConverter().getCertificate(
            CMSSignedData(Streams.readAll(signedContent)).certificates.getMatches(null).first()
        )
    }

    private fun String.toX509Certificate(): X509Certificate {
        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(trim().byteInputStream()) as X509Certificate
    }

    private fun String.saveTemplate(): NotificationTemplateWithMeta {
        return notificationTemplateService.save(
            Json.mapper.convert(
                stringFromResource(
                    this
                ),
                NotificationTemplateWithMeta::class.java
            )!!
        )
    }
}
