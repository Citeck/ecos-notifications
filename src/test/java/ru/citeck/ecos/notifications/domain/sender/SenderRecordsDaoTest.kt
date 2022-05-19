package ru.citeck.ecos.notifications.domain.sender

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.notifications.domain.sender.api.records.NotificationsSenderRecordsDao
import ru.citeck.ecos.notifications.web.rest.TestUtil
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records3.spring.config.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.records3.spring.web.rest.RecordsRestApi

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
@DirtiesContext
class SenderRecordsDaoTest {

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var factoryConfig: RecordsServiceFactoryConfiguration

    companion object {
        const val QUERY = "query"
        const val LANGUAGE = "language"
        const val RECORDS = "records"
        const val ATTRIBUTES = "attributes"

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
        val resultActions = mockMvc.perform(MockMvcRequestBuilders.post(TestUtil.URL_RECORDS_QUERY)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(jsonString))
        resultActions.andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.$RECORDS").isEmpty)
    }

/*    @Test
    fun createRecord() {
        val jsonString = getJsonToSend(JSONObject()
            .put(RECORDS, JSONArray().put(
                JSONObject().put(SenderTestData.PROP_ID, SenderTestData.getEmptyId())
                    .put(ATTRIBUTES, JSONObject()
                        .put(SenderTestData.PROP_NAME, "Test notifications sender - Create")
                        .put(SenderTestData.PROP_ORDER, 3f))
            )).toString(2))
        val resultActions = mockMvc.perform(MockMvcRequestBuilders.post(TestUtil.URL_RECORDS_MUTATE)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(jsonString))
        resultActions.andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.$RECORDS").isNotEmpty)
    }*/
}
