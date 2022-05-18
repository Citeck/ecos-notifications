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
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.records2.predicate.model.Predicates
import java.util.function.Predicate

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
@DirtiesContext
class SenderServiceContextTest {
    @Autowired
    private lateinit var service: NotificationsSenderService
    @Autowired
    private lateinit var repository: NotificationsSenderRepository

    @Test
    fun createTest() {
        val testDto = SenderTestData.getNewSender()
        val testDtoWithMeta = service.save(testDto)

        Assert.assertNotNull(testDtoWithMeta.modifier)
        val createdDto = testDtoWithMeta.toDto()
        testDto.id = createdDto.id

        val senderDtoMatcher: Matcher<NotificationsSenderDto> = Matchers.`is`(testDto)
        Assert.assertThat(createdDto, senderDtoMatcher)
    }

    @Test
    fun deleteTest() {
        service.delete(SenderTestData.TEST_SENDER_ID)
        Assert.assertNull(service.getSenderById(SenderTestData.TEST_SENDER_ID))
    }

    @Test
    fun modifyTest() {
        val testDto = SenderTestData.getTestSender()
        service.save(testDto)
        val senderDtoMatcher: Matcher<NotificationsSenderDto> = Matchers.`is`(testDto)
        Assert.assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto(), senderDtoMatcher)
        testDto.order = 5.0f
        service.save(testDto)
        Assert.assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto(), senderDtoMatcher)
        testDto.enabled = true
        testDto.name = "changed name"
        service.save(testDto)
        Assert.assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto(), senderDtoMatcher)
    }

    @Test
    fun queryTest(){
     /*   Predicates.eq()
        service.getAll(50, 0, )*/
    }
}
