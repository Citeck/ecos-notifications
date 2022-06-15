package ru.citeck.ecos.notifications.domain.sender

import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.eapps.NotificationsSenderArtifactHandler
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
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
        val senderDtoMatcher: Matcher<NotificationsSenderDto> = Matchers.`is`(testDto)
        Assert.assertThat(repository.findOneByExtId(SenderTestData.TEST_SENDER_ID).get().toDto(), senderDtoMatcher)

        handler.deleteArtifact(SenderTestData.TEST_SENDER_ID)
        Assert.assertFalse(repository.findOneByExtId(SenderTestData.TEST_SENDER_ID).isPresent)
    }

}
