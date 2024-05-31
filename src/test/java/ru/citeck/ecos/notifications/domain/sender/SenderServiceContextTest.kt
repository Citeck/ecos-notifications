package ru.citeck.ecos.notifications.domain.sender

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.converter.toEntity
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext
class SenderServiceContextTest {
    @Autowired
    private lateinit var service: NotificationsSenderService

    @Autowired
    private lateinit var repository: NotificationsSenderRepository
    private val itemsCount = 10

    @AfterEach
    fun clearRepository() {
        repository.deleteAll()
        repository.flush()
    }

    @Test
    fun createTest() {
        val testDto = SenderTestData.getNewSender()
        val testDtoWithMeta = service.save(testDto)

        assertNotNull(testDtoWithMeta.modifier)
        val createdDto = testDtoWithMeta.toDto()
        testDto.id = createdDto.id

        assertThat(createdDto).isEqualTo(testDto)
    }

    @Test
    fun deleteTest() {
        repository.save(SenderTestData.getTestSender().toEntity())
        service.delete(SenderTestData.TEST_SENDER_ID)
        assertNull(service.getSenderById(SenderTestData.TEST_SENDER_ID))
    }

    @Test
    fun modifyTest() {
        val testDto = SenderTestData.getTestSender()
        service.save(testDto)
        assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto()).isEqualTo(testDto)
        testDto.order = 5f
        service.save(testDto)
        assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto()).isEqualTo(testDto)
        testDto.enabled = true
        testDto.name = "changed name"
        service.save(testDto)
        assertThat(service.getSenderById(SenderTestData.TEST_SENDER_ID)!!.toDto()).isEqualTo(testDto)
    }

    @Test
    fun queryTest() {
        repository.deleteAll()

        val testDto = SenderTestData.getTestSender()
        repository.save(testDto.toEntity())
        var result = service.getAll(
            50,
            0,
            Predicates.eq(
                "${SenderTestData.PROP_NAME}?str",
                SenderTestData.getTestSender().name
            ),
            emptyList()
        )
        assertEquals(result.size, 1)

        assertThat(result[0].toDto()).isEqualTo(testDto)

        repository.saveAll(generateEntities())
        result = service.getAll(
            50,
            0,
            Predicates.and(
                Predicates.eq(SenderTestData.PROP_ENABLED, true),
                Predicates.gt(SenderTestData.PROP_ORDER, 0)
            ),
            emptyList()
        )
        assertEquals(result.size, itemsCount)

        result = service.getAll()
        assertEquals(result.size, itemsCount + 1)

        result = service.getAll(50, 0, Predicates.eq("invalidPropName", true), emptyList())
        assertEquals(result.size, itemsCount + 1)
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
