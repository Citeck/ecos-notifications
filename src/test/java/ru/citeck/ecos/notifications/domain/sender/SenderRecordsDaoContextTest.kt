package ru.citeck.ecos.notifications.domain.sender

import org.hamcrest.Matchers.stringContainsInOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.web.rest.TestUtil
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.webapp.lib.spring.context.api.rest.RecordsRestApi
import ru.citeck.ecos.webapp.lib.spring.context.records.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext
class SenderRecordsDaoContextTest {

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var factoryConfig: RecordsServiceFactoryConfiguration

    @Autowired
    private lateinit var service: NotificationsSenderService

    @Autowired
    private lateinit var repository: NotificationsSenderRepository

    var testSenderJsonValue: String? = null

    companion object {
        const val QUERY = "query"
        const val LANGUAGE = "language"
        const val RECORDS = "records"
        const val ATTRIBUTES = "attributes"
        const val STR = "?str"

        @JvmStatic
        private fun getJsonToSend(json: String): String {
            return json.replace("\\/", "/")
        }
    }

    @BeforeEach
    fun setUp() {
        val recordsApi = RecordsRestApi(factoryConfig)
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build()
    }

    @Test
    fun query_WithEmptyResult() {
        val jsonString = DataValue.createObj()
            .set(
                QUERY,
                DataValue.createObj()
                    .set("sourceId", NotificationsSenderRecordsDao.ID)
                    .set(LANGUAGE, PredicateService.LANGUAGE_PREDICATE)
                    .set(
                        QUERY,
                        DataValue.createObj()
                            .set("t", "eq")
                            .set("att", SenderTestData.PROP_ORDER)
                            .set("val", -10f)
                    )
                    .set(
                        ATTRIBUTES,
                        DataValue.createObj()
                            .set(SenderTestData.PROP_NAME, SenderTestData.PROP_NAME)
                            .set(SenderTestData.PROP_EXT_ID, SenderTestData.PROP_EXT_ID)
                    )
            ).toString()
        val resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(jsonString)
        )
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isEmpty)
    }

    @Test
    fun createRecord() {
        val jsonString = getJsonToSend(
            DataValue.createObj()
                .set(
                    RECORDS,
                    DataValue.createArr().add(
                        DataValue.createObj()
                            .set(SenderTestData.PROP_ID, SenderTestData.getEmptyId())
                            .set(
                                ATTRIBUTES,
                                DataValue.createObj()
                                    .set(SenderTestData.PROP_NAME, "Test notifications sender - Create")
                                    .set(SenderTestData.PROP_ORDER, 3f)
                            )
                    )
                ).toString()
        )
        val resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(jsonString)
        )
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isNotEmpty)
            .andExpect(
                jsonPath(
                    "$." + RECORDS + "[0]." + SenderTestData.PROP_ID,
                    stringContainsInOrder(listOf("notifications-sender@"))
                )
            )
    }

    @Test
    fun deleteRecord() {
        createTestSender()
        val jsonString = getJsonToSend(
            DataValue.createObj()
                .set(
                    RECORDS,
                    DataValue.createArr().add(
                        SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID
                    )
                ).toString()
        )
        mockMvc.perform(
            post(TestUtil.URL_RECORDS_DELETE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(jsonString)
        )
            .andExpect(status().isOk)
        mockMvc.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(getTestSenderJson())
        )
            .andDo(MockMvcResultHandlers.print())
        // .andExpect(jsonPath("$.." + SenderTestData.PROP_NAME + STR).value(IsNull.nullValue()))
    }

    @Test
    fun updateRecord() {
        createTestSender()
        val newName = "Updated Test Notifications Sender"
        val jsonString = getJsonToSend(
            DataValue.createObj()
                .set(
                    RECORDS,
                    DataValue.createArr().add(
                        DataValue.createObj().set(
                            SenderTestData.PROP_ID,
                            SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID
                        )
                            .set(
                                ATTRIBUTES,
                                DataValue.createObj()
                                    .set(SenderTestData.PROP_NAME, newName)
                                    .set(SenderTestData.PROP_ORDER, 10f)
                            )
                    )
                ).toString()
        )
        var resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(jsonString)
        )
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isNotEmpty)

        resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(getTestSenderJson())
        )
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$." + RECORDS).isNotEmpty)
            .andExpect(jsonPath("$.." + SenderTestData.PROP_NAME + STR).value(newName))
    }

    @Test
    fun queryByPredicates_withOneResult() {
        deleteAll()
        createTestSender()
        val resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(getTestSenderJson())
        )
        resultActions
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isNotEmpty)
            /*.andExpect(jsonPath("$." + RECORDS + "[0]." + SenderTestData.PROP_ID)
                .value(SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID))*/
            .andExpect(
                jsonPath("$.." + SenderTestData.PROP_NAME + STR)
                    .value(SenderTestData.getTestSender().name!!)
            )
    }

    private fun createTestSender() {
        service.save(SenderTestData.getTestSender())
    }

    private fun getTestSenderJson(): String {
        if (testSenderJsonValue != null) {
            return testSenderJsonValue!!
        }
        testSenderJsonValue = getJsonToSend(
            DataValue.createObj()
                .set(RECORDS, DataValue.createArr().add(SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID))
                .set(
                    ATTRIBUTES,
                    DataValue.createArr()
                        .add(SenderTestData.PROP_ID + STR)
                        .add(SenderTestData.PROP_ENABLED)
                        .add(SenderTestData.PROP_NAME + STR)
                        .add(SenderTestData.PROP_ORDER)
                )
                .toString()
        )
        return testSenderJsonValue!!
    }

    private fun deleteAll() {
        repository.deleteAll()
        repository.flush()
    }
}
