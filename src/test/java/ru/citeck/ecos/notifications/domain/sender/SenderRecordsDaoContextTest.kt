package ru.citeck.ecos.notifications.domain.sender

import org.hamcrest.Matchers.stringContainsInOrder
//import org.hamcrest.core.IsNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import ru.citeck.ecos.notifications.web.rest.TestUtil
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records3.spring.config.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.records3.spring.web.rest.RecordsRestApi

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
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

    @Before
    fun setUp() {
        val recordsApi = RecordsRestApi(factoryConfig)
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build()
    }

    @Test
    fun query_WithEmptyResult() {
        val jsonString = JSONObject()
            .put(QUERY,
                JSONObject().put("sourceId", NotificationsSenderRecordsDao.ID)
                    .put(LANGUAGE, PredicateService.LANGUAGE_PREDICATE)
                    .put(QUERY, JSONObject()
                        .put("t", "eq")
                        .put("att", SenderTestData.PROP_ORDER)
                        .put("val", -10f))
                    .put(ATTRIBUTES, JSONObject()
                        .put(SenderTestData.PROP_NAME, SenderTestData.PROP_NAME)
                        .put(SenderTestData.PROP_EXT_ID, SenderTestData.PROP_EXT_ID))).toString(2)
        val resultActions = mockMvc.perform(post(TestUtil.URL_RECORDS_QUERY)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(jsonString))
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isEmpty)
    }

    @Test
    fun createRecord() {
        val jsonString = getJsonToSend(JSONObject()
            .put(RECORDS, JSONArray().put(
                JSONObject().put(SenderTestData.PROP_ID, SenderTestData.getEmptyId())
                    .put(ATTRIBUTES, JSONObject()
                        .put(SenderTestData.PROP_NAME, "Test notifications sender - Create")
                        .put(SenderTestData.PROP_ORDER, 3f))
            )).toString(2))
        val resultActions = mockMvc.perform(post(TestUtil.URL_RECORDS_MUTATE)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(jsonString))
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isNotEmpty)
            .andExpect(jsonPath("$." + RECORDS + "[0]." + SenderTestData.PROP_ID,
                stringContainsInOrder(listOf("notifications-sender@"))))
    }

    @Test
    fun deleteRecord() {
        createTestSender()
        val jsonString = getJsonToSend(JSONObject()
            .put(RECORDS, JSONArray().put(SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID
            )).toString(2))
        mockMvc.perform(
            post(TestUtil.URL_RECORDS_DELETE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(jsonString))
            .andExpect(status().isOk)
        mockMvc.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(getTestSenderJson()))
            .andDo(MockMvcResultHandlers.print())
        //.andExpect(jsonPath("$.." + SenderTestData.PROP_NAME + STR).value(IsNull.nullValue()))
    }

    @Test
    fun updateRecord() {
        createTestSender()
        val newName = "Updated Test Notifications Sender"
        val jsonString = getJsonToSend(JSONObject()
            .put(RECORDS, JSONArray().put(
                JSONObject().put(SenderTestData.PROP_ID,
                    SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID)
                    .put(ATTRIBUTES, JSONObject()
                        .put(SenderTestData.PROP_NAME, newName)
                        .put(SenderTestData.PROP_ORDER, 10f))
            )).toString(2))
        var resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(jsonString))
        resultActions.andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isNotEmpty)

        resultActions = mockMvc.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(getTestSenderJson()))
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
                .content(getTestSenderJson()))
        resultActions
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.$RECORDS").isNotEmpty)
            /*.andExpect(jsonPath("$." + RECORDS + "[0]." + SenderTestData.PROP_ID)
                .value(SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID))*/
            .andExpect(jsonPath("$.." + SenderTestData.PROP_NAME + STR)
                .value(SenderTestData.getTestSender().name!!))
    }

    private fun createTestSender() {
        service.save(SenderTestData.getTestSender())
    }

    private fun getTestSenderJson(): String {
        if (testSenderJsonValue != null) {
            return testSenderJsonValue!!
        }
        testSenderJsonValue = getJsonToSend(JSONObject()
            .put(RECORDS, JSONArray().put(SenderTestData.getEmptyId() + SenderTestData.TEST_SENDER_ID))
            .put(ATTRIBUTES, JSONArray()
                .put(SenderTestData.PROP_ID + STR)
                .put(SenderTestData.PROP_ENABLED)
                .put(SenderTestData.PROP_NAME + STR)
                .put(SenderTestData.PROP_ORDER))
            .toString(2))
        return testSenderJsonValue!!
    }

    private fun deleteAll() {
        repository.deleteAll()
        repository.flush()
    }
}
