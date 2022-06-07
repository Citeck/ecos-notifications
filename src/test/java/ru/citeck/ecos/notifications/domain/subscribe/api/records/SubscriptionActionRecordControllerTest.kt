package ru.citeck.ecos.notifications.domain.subscribe.api.records

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.hamcrest.Matchers.stringContainsInOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
import ru.citeck.ecos.notifications.web.rest.TestUtil
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.context.api.rest.RecordsRestApi
import ru.citeck.ecos.webapp.lib.spring.context.records.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
class SubscriptionActionRecordControllerTest {

    @Autowired
    private lateinit var actionService: ActionService

    @Autowired
    private lateinit var factoryConfig: RecordsServiceFactoryConfiguration

    private lateinit var mockRecordsApi: MockMvc

    private val mapper: ObjectMapper = ObjectMapper()

    val mutatePayload = """
            {
              "records": [
                {
                  "id": "notifications/subscription-action@",
                  "attributes": {
                    "subscriberId": "server-1234|admin",
                    "eventType": "task.assign",
                    "action": {
                      "type": "FIREBASE_NOTIFICATION",
                      "config": {
                        "fireBaseClientRegToken": "token-123",
                        "deviceType": "android",
                        "templateId": "default-template-test"
                      },
                      "condition": true,
                      "customData": [
                        {
                          "variable": "req",
                          "record": "some-record-id",
                          "attributes": {
                            "number": "terRegNumber",
                            "package": ".atts(n:\"packageContent\"){id}",
                            "employee": "reportablePerson",
                            "type": "_type"
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        val recordsApi = RecordsRestApi(factoryConfig)
        this.mockRecordsApi = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build()
    }

    @Test
    fun `successful save subscription action`() {
        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(mutatePayload)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.records[0].id", stringContainsInOrder(listOf("subscription-action@"))))
    }

    @Test
    fun `save subscription action and check action payload config`() {
        val result = mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(mutatePayload)
        ).andReturn()

        val action = getActionFromResponse(result)
        val configNode: JsonNode = mapper.readValue(
            action.configJSON,
            JsonNode::class.java
        )

        assertThat(configNode["fireBaseClientRegToken"].asText()).isEqualTo("token-123")
        assertThat(configNode["deviceType"].asText()).isEqualTo("android")
        assertThat(configNode["templateId"].asText()).isEqualTo("default-template-test")
    }

    @Test
    fun `update subscription action config`() {
        val result = mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(mutatePayload)
        ).andReturn()

        val action = getActionFromResponse(result)

        val updateConfigPayload = """
            {
              "records": [
                {
                  "id": "notifications/subscription-action@${action.id}",
                  "attributes": {
                    "updateActionConfig": {
                      "fireBaseClientRegToken": "new-token-33",
                      "deviceType": "new-device-ios",
                      "templateId": "new-template",
                      "locale": "en_gb"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(updateConfigPayload)
        )

        val updatedAction = actionService.findById(action.id).get()

        val configNode: JsonNode = mapper.readValue(
            updatedAction.configJSON,
            JsonNode::class.java
        )

        assertThat(configNode["fireBaseClientRegToken"].asText()).isEqualTo("new-token-33")
        assertThat(configNode["deviceType"].asText()).isEqualTo("new-device-ios")
        assertThat(configNode["templateId"].asText()).isEqualTo("new-template")
        assertThat(configNode["locale"].asText()).isEqualTo("en_gb")
    }

    @Test
    fun `check action payload with custom data`() {
        val result = mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(mutatePayload)
        ).andReturn()

        val customData = getActionFromResponse(result).customData

        assertThat(customData.size).isEqualTo(1)

        assertThat(customData.take(1)[0].variable).isEqualTo("req")
        assertThat(customData.take(1)[0].record).isEqualTo("some-record-id")

        assertThat(customData.take(1)[0].attributes.size).isEqualTo(4)
        assertThat(customData.take(1)[0].attributes).contains(
            entry("number", "terRegNumber"),
            entry("package", ".atts(n:\"packageContent\"){id}"),
            entry("employee", "reportablePerson"),
            entry("type", "_type")
        )
    }

    private fun getActionFromResponse(result: MvcResult): ActionEntity {
        val contentAsString = result.response.contentAsString

        val responseNode: JsonNode = mapper.readValue(
            contentAsString,
            JsonNode::class.java
        )

        val id = responseNode.get("records")[0].get("id").asText()

        val actionRef = RecordRef.valueOf(id)

        val action = actionService.findById(actionRef.id.toLong())

        return action.get()
    }
}
