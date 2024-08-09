package ru.citeck.ecos.notifications.domain.sender

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext
class SenderRecordsDaoContextTest {

    @Autowired
    private lateinit var service: NotificationsSenderService

    @Autowired
    private lateinit var repository: NotificationsSenderRepository

    @Autowired
    private lateinit var recordsService: RecordsService

    companion object {
        const val STR = "?str"
    }

    @Test
    fun query_WithEmptyResult() {

        val query = RecordsQuery.create()
            .withSourceId(NotificationsSenderRecordsDao.ID)
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withQuery(Predicates.eq(SenderTestData.PROP_ORDER, -10f))
            .build()

        val queryRes = recordsService.query(
            query,
            mapOf(
                SenderTestData.PROP_NAME to SenderTestData.PROP_NAME,
                SenderTestData.PROP_EXT_ID to SenderTestData.PROP_EXT_ID
            )
        )
        assertThat(queryRes.getRecords()).isEmpty()
    }

    @Test
    fun createRecord() {

        val recordAtts = RecordAtts(SenderTestData.getEmptyId())
        recordAtts[SenderTestData.PROP_NAME] = "Test notifications sender - Create"
        recordAtts[SenderTestData.PROP_ORDER] = 3f

        val mutRes = recordsService.mutate(recordAtts)

        assertThat(mutRes.toString()).contains("notifications-sender@")
    }

    @Test
    fun deleteRecord() {

        createTestSender()

        val recId = SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID
        recordsService.delete(recId)
        val notExists = recordsService.getAtt(recId, "_notExists?bool!").asBoolean()

        assertThat(notExists).isTrue
    }

    @Test
    fun updateRecord() {

        createTestSender()

        val newName = "Updated Test Notifications Sender"

        val ref = SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID
        val recordAtts = RecordAtts(ref)
        recordAtts[SenderTestData.PROP_NAME] = newName
        recordAtts[SenderTestData.PROP_ORDER] = 10f

        recordsService.mutate(recordAtts)

        assertThat(recordsService.getAtt(ref, SenderTestData.PROP_NAME + STR).asText()).isEqualTo(newName)
    }

    @Test
    fun queryByPredicates_withOneResult() {
        deleteAll()
        createTestSender()

        val value = recordsService.getAtt(
            SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID,
            SenderTestData.PROP_NAME + STR
        ).asText()
        assertThat(value).isEqualTo(SenderTestData.getTestSender().name)
    }

    private fun createTestSender() {
        service.save(SenderTestData.getTestSender())
    }

    private fun deleteAll() {
        repository.deleteAll()
        repository.flush()
    }
}
