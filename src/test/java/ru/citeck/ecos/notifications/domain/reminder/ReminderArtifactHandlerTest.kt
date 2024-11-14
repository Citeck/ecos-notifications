package ru.citeck.ecos.notifications.domain.reminder

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.reminder.dto.Reminder
import ru.citeck.ecos.notifications.domain.reminder.dto.ReminderType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReminderArtifactHandlerTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))
        }
    }

    @Test
    fun `check reminder meta data after deploy`() {
        val ref = EntityRef.create(
            AppName.NOTIFICATIONS,
            "reminder",
            "test-meta-reminder"
        )
        val reminder = recordsService.getAtts(ref, Reminder::class.java)

        assertThat(reminder).isNotNull
        assertThat(reminder.id).isEqualTo("test-meta-reminder")
        assertThat(reminder.name).isEqualTo(
            MLText(
                I18nContext.RUSSIAN to "Тестовое напоминание мета",
                I18nContext.ENGLISH to "Test reminder meta"
            )
        )
        assertThat(reminder.enabled).isFalse
        assertThat(reminder.reminderType).isEqualTo(ReminderType.CERTIFICATE_EXPIRATION)
        assertThat(reminder.certificates).containsExactlyInAnyOrder(
            "emodel/secret@certificate-test-expiration-meta".toEntityRef(),
            "emodel/secret@certificate-test-expiration-meta-2".toEntityRef()
        )
        assertThat(reminder.notificationTemplate).isEqualTo(
            "notifications/template@cert-expiration-test-meta".toEntityRef()
        )
        assertThat(reminder.recipients).containsExactlyInAnyOrder(
            "emodel/person@user1".toEntityRef(),
            "emodel/person@user2".toEntityRef()
        )
        assertThat(reminder.reminderThresholdDurations).containsExactlyInAnyOrder("15d", "5d")
    }
}
