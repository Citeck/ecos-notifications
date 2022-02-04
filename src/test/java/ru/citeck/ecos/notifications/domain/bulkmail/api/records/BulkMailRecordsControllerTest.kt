package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.stringJsonFromResource
import ru.citeck.ecos.notifications.web.rest.TestUtil
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.spring.config.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.records3.spring.web.rest.RecordsRestApi

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class BulkMailRecordsControllerTest {

    companion object {
        private val testTemplateRef = RecordRef.create("notifications", "template", "test-template")
        private val docRef = RecordRef.valueOf("doc@test-document")

        private val mapper: ObjectMapper = ObjectMapper()
    }

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    @Autowired
    private lateinit var factoryConfig: RecordsServiceFactoryConfiguration

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var recordsService: RecordsService

    private lateinit var mockRecordsApi: MockMvc

    @Before
    fun setUp() {
        val recordsApi = RecordsRestApi(factoryConfig)
        this.mockRecordsApi = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build()

        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create(docRef.sourceId)
                .addRecord(docRef.id, DocDto())
                .build()
        )
    }

    @Test
    fun `create new bulk mail via mutate`() {

        val result = mockRecordsApi.perform(
            MockMvcRequestBuilders.post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(
                    """
                        {
                          "records": [
                            {
                              "id": "notifications/bulk-mail@",
                              "attributes": {
                                "title": "Foo",
                                "body": "Bar",
                                "status": "new",
                                "type": "EMAIL_NOTIFICATION",
                                "record": "$docRef",
                                "template": "$testTemplateRef",
                                "recipientsData": {
                                  "recipients": [
                                    "petya@mail.ru",
                                    "vasya@mail.ru"
                                  ]
                                },
                                "config": {
                                  "param0": 0,
                                  "param1": "value1"
                                }
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                )
        ).andReturn()

        val bulkMail = getBulkMailFromResponse(result)

        assertThat(bulkMail.extId).isNotBlank
        assertThat(bulkMail.title).isEqualTo("Foo")
        assertThat(bulkMail.body).isEqualTo("Bar")
        assertThat(bulkMail.status).isEqualTo("new")
        assertThat(bulkMail.type).isEqualTo(NotificationType.EMAIL_NOTIFICATION)
        assertThat(bulkMail.record).isEqualTo(docRef)
        assertThat(bulkMail.template).isEqualTo(testTemplateRef)
        assertThat(bulkMail.recipientsData).isEqualTo(
            ObjectData.create(
                """
                {
                  "recipients": [
                    "petya@mail.ru",
                    "vasya@mail.ru"
                  ]
                }
            """.trimIndent()
            )
        )
        assertThat(bulkMail.config).isEqualTo(
            ObjectData.create(
                """
                {
                  "param0": 0,
                  "param1": "value1"
                }
            """.trimIndent()
            )
        )

    }

    @Test
    fun `check exists bulk mail payload`() {
        val toSave = BulkMailDto(
            id = null,
            recipientsData = ObjectData.create(
                """
                {
                  "recipients": [
                    "petya@mail.ru",
                    "vasya@mail.ru"
                  ]
                }
            """.trimIndent()
            ),
            record = docRef,
            template = testTemplateRef,
            type = NotificationType.EMAIL_NOTIFICATION,
            title = "Hello",
            body = "World",
            config = ObjectData.create(
                """
                {
                  "param0": 0,
                  "param1": "value1"
                }
            """.trimIndent()
            ),
            status = "new"
        )

        val saved = bulkMailDao.save(
            toSave
        )

        mockRecordsApi.perform(
            MockMvcRequestBuilders.post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(
                    """
                    {
                      "record": "notifications/bulk-mail@${saved.extId}",
                      "attributes": {
                        "title": "title",
                        "body": "body",
                        "status": "status",
                        "record": "record",
                        "type": "type",
                        "template": "template",
                        "recipientsData": "recipientsData?json",
                        "config": "config?json"
                      }
                    }
                """.trimIndent()
                )
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("$.id", `is`("bulk-mail@${saved.extId}")))
            .andExpect(jsonPath("$.attributes.title", `is`("Hello")))
            .andExpect(jsonPath("$.attributes.body", `is`("World")))
            .andExpect(jsonPath("$.attributes.status", `is`("new")))
            .andExpect(jsonPath("$.attributes.type", `is`("EMAIL_NOTIFICATION")))
            .andExpect(jsonPath("$.attributes.record", `is`("Документ №123")))
            .andExpect(jsonPath("$.attributes.template", `is`("Test template")))
            .andExpect(jsonPath("$.attributes.recipientsData.recipients[*]", Matchers.hasSize<Any>(2)))
            .andExpect(jsonPath("$.attributes.recipientsData.recipients[0]", `is`("petya@mail.ru")))
            .andExpect(jsonPath("$.attributes.recipientsData.recipients[1]", `is`("vasya@mail.ru")))
            .andExpect(jsonPath("$.attributes.config[*]", Matchers.hasSize<Any>(2)))
            .andExpect(jsonPath("$.attributes.config.param0", `is`(0)))
            .andExpect(jsonPath("$.attributes.config.param1", `is`("value1")))
    }

    @Test
    fun `update existing bulk mail via mutate`() {
        val savedExtId = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = ObjectData.create(
                    """
                        {
                          "recipients": [
                            "petya@mail.ru",
                            "vasya@mail.ru"
                          ]
                        }
                    """.trimIndent()
                ),
                record = docRef,
                template = testTemplateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                title = "Hello",
                body = "World",
                config = ObjectData.create(
                    """
                        {
                          "param0": 0,
                          "param1": "value1"
                        }
                    """.trimIndent()
                ),
                status = "new"
            )
        ).extId

        val mutateResult = mockRecordsApi.perform(
            MockMvcRequestBuilders.post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(
                    """
                        {
                          "records": [
                            {
                              "id": "notifications/bulk-mail@$savedExtId",
                              "attributes": {
                                "title": "Foo_2",
                                "body": "Bar_2",
                                "status": "new_2",
                                "type": "FIREBASE_NOTIFICATION",
                                "record": "$testTemplateRef",
                                "template": "$docRef",
                                "recipientsData": {
                                  "recipients": [
                                    "petya_2@mail.ru"
                                  ]
                                },
                                "config": {
                                  "param0": 100,
                                  "param1": "value1_2",
                                  "param2": "value2"
                                }
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                )
        ).andReturn()

        val updatedBulkMail = getBulkMailFromResponse(mutateResult)

        assertThat(updatedBulkMail.extId).isEqualTo(savedExtId)
        assertThat(updatedBulkMail.title).isEqualTo("Foo_2")
        assertThat(updatedBulkMail.body).isEqualTo("Bar_2")
        assertThat(updatedBulkMail.status).isEqualTo("new_2")
        assertThat(updatedBulkMail.type).isEqualTo(NotificationType.FIREBASE_NOTIFICATION)
        assertThat(updatedBulkMail.record).isEqualTo(testTemplateRef)
        assertThat(updatedBulkMail.template).isEqualTo(docRef)
        assertThat(updatedBulkMail.recipientsData).isEqualTo(
            ObjectData.create(
                """
                {
                  "recipients": [
                    "petya_2@mail.ru"
                  ]
                }
            """.trimIndent()
            )
        )
        assertThat(updatedBulkMail.config).isEqualTo(
            ObjectData.create(
                """
                {
                  "param0": 100,
                  "param1": "value1_2",
                  "param2": "value2"
                }
            """.trimIndent()
            )
        )
    }

    private fun getBulkMailFromResponse(result: MvcResult): BulkMailDto {
        val contentAsString = result.response.contentAsString

        val responseNode: JsonNode = mapper.readValue(
            contentAsString,
            JsonNode::class.java
        )

        val id = responseNode.get("records")[0].get("id").asText()

        val ref = RecordRef.valueOf(id)

        val bulkMail = bulkMailDao.findByExtId(ref.id)

        return bulkMail!!
    }

    class DocDto(
        @AttName("?disp")
        val name: String = "Документ №123"
    )

}
