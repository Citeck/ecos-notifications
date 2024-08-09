package ru.citeck.ecos.notifications.domain.subscribe.api.records

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.webapp.api.entity.EntityRef
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

    @Autowired
    private lateinit var recordsService: RecordsService

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

    @Test
    fun `successful save subscription action`() {
        val records = DataValue.of(mutatePayload)["records"].asList(RecordAtts::class.java)
        val res = recordsService.mutate(records).first()
        assertThat(res.toString()).contains("subscription-action@")
    }

    @Test
    fun `save subscription action and check action payload config`() {

        val records = DataValue.of(mutatePayload)["records"].asList(RecordAtts::class.java)
        val res = recordsService.mutate(records).first()
        val action = getActionFromRef(res)

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

        val records = DataValue.of(mutatePayload)["records"].asList(RecordAtts::class.java)
        val res = recordsService.mutate(records).first()
        val action = getActionFromRef(res)

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

        val updateAtts = DataValue.of(updateConfigPayload)["records"].asList(RecordAtts::class.java)
        recordsService.mutate(updateAtts)

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

        val records = DataValue.of(mutatePayload)["records"].asList(RecordAtts::class.java)
        val res = recordsService.mutate(records).first()
        val customData = getActionFromRef(res).customData

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

    private fun getActionFromRef(result: EntityRef): ActionEntity {
        return actionService.findById(result.getLocalId().toLong()).get()
    }
}
