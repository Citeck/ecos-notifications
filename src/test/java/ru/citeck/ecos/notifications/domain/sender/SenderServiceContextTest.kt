package ru.citeck.ecos.notifications.domain.sender

import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.converter.toEntity
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.records2.predicate.model.Predicates

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
@DirtiesContext
class SenderServiceContextTest {
    @Autowired
    private lateinit var service: NotificationsSenderService

    @Autowired
    private lateinit var repository: NotificationsSenderRepository
    private val itemsCount = 10

    @After
    fun clearRepository() {
        repository.deleteAll()
        repository.flush()
    }

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
        repository.save(SenderTestData.getTestSender().toEntity())
        service.delete(SenderTestData.TEST_SENDER_ID)
        Assert.assertNull(service.getSenderById(SenderTestData.TEST_SENDER_ID))
    }

    @Test
    fun modifyTest() {
        val testDto = SenderTestData.getTestSender()
        service.save(testDto)
        val senderDtoMatcher: Matcher<NotificationsSenderDto> = Matchers.`is`(testDto)
        Assert.assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto(), senderDtoMatcher)
        testDto.order = 5f
        service.save(testDto)
        Assert.assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto(), senderDtoMatcher)
        testDto.enabled = true
        testDto.name = "changed name"
        service.save(testDto)
        Assert.assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto(), senderDtoMatcher)
    }

    @Test
    fun queryTest() {
        val testDto = SenderTestData.getTestSender()
        repository.save(testDto.toEntity())
        var result = service.getAll(50, 0, Predicates.eq(SenderTestData.PROP_NAME,
            SenderTestData.getTestSender().name), null)
        Assert.assertEquals(result.size, 1)

        val senderDtoMatcher: Matcher<NotificationsSenderDto> = Matchers.`is`(testDto)
        Assert.assertThat(result[0].toDto(), senderDtoMatcher)

        repository.saveAll(generateEntities())
        result = service.getAll(50, 0,
            Predicates.and(Predicates.eq(SenderTestData.PROP_ENABLED, true),
            Predicates.gt(SenderTestData.PROP_ORDER, 0)), null)
        Assert.assertEquals(result.size, itemsCount)

        result = service.getAll()
        Assert.assertEquals(result.size, itemsCount + 1)

        result = service.getAll(50,0,Predicates.eq("invalidPropName", true), null)
        Assert.assertEquals(result.size, itemsCount + 1)
    }

    private fun generateEntities(): List<NotificationsSenderEntity> {
        val result = mutableListOf<NotificationsSenderEntity>()
        for (i in 1..itemsCount) {
            val dto = SenderTestData.getNewSender()
            dto.name = "sender name $i"
            dto.order = 2f
            dto.enabled = true
            result.add(dto.toEntity())
        }
        return result
    }
}
