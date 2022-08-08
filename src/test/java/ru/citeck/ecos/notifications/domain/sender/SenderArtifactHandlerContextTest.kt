package ru.citeck.ecos.notifications.domain.sender

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.eapps.NotificationsSenderArtifactHandler
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext
class SenderArtifactHandlerContextTest {

    @Autowired
    private lateinit var repository: NotificationsSenderRepository

    @Autowired
    private lateinit var handler: NotificationsSenderArtifactHandler

    @Test
    fun testHandler() {
        repository.deleteAll()

        val testDto = SenderTestData.getTestSender()
        handler.deployArtifact(testDto)
        assertThat(repository.findOneByExtId(SenderTestData.TEST_SENDER_ID).get().toDto()).isEqualTo(testDto)

        handler.deleteArtifact(SenderTestData.TEST_SENDER_ID)
        assertFalse(repository.findOneByExtId(SenderTestData.TEST_SENDER_ID).isPresent)
    }
}
