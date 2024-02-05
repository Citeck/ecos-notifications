package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailBatchConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
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
import ru.citeck.ecos.webapp.lib.spring.context.api.rest.RecordsRestApi
import ru.citeck.ecos.webapp.lib.spring.context.records.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.util.*

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
class BulkMailRecordsControllerTest {

    companion object {
        private val testTemplateRef = RecordRef.create("notifications", "template", "test-template")
        private val docRef = RecordRef.valueOf("doc@test-document")

        private val harryRef = RecordRef.valueOf("alfresco/people@harry")
        private val severusRef = RecordRef.valueOf("alfresco/people@severus")

        private val departmentGroupRef = RecordRef.valueOf("alfresco/authority@GROUP_department")

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

    @BeforeEach
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

        val now = Instant.now()

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
                                "name": "Bulk mail №123",
                                "status": "new",
                                "type": "EMAIL_NOTIFICATION",
                                "record": "$docRef",
                                "template": "$testTemplateRef",
                                "recipientsData": {
                                  "refs": [
                                    "alfresco/ray@bradbury",
                                    "$harryRef",
                                    "$departmentGroupRef"
                                  ],
                                  "fromUserInput": "one,two",
                                  "custom": {
                                    "field0": 451,
                                    "field1": "Fahrenheit"
                                  }
                                },
                                "config": {
                                  "batchConfig": {
                                    "size": 50,
                                    "personalizedMails": true
                                  },
                                  "allBcc": true,
                                  "delayedSend": ${now.toEpochMilli()}
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
        assertThat(bulkMail.name).isEqualTo("Bulk mail №123")
        assertThat(bulkMail.status).isEqualTo("new")
        assertThat(bulkMail.type).isEqualTo(NotificationType.EMAIL_NOTIFICATION)
        assertThat(bulkMail.record).isEqualTo(docRef)
        assertThat(bulkMail.template).isEqualTo(testTemplateRef)
        assertThat(bulkMail.recipientsData).isEqualTo(
            BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("alfresco/ray@bradbury"),
                    harryRef,
                    departmentGroupRef
                ),
                fromUserInput = "one,two",
                custom = ObjectData.create(
                    """
                     {
                        "field0": 451,
                        "field1": "Fahrenheit"
                      }
                    """.trimIndent()
                )
            )
        )
        assertThat(bulkMail.config).isEqualTo(
            BulkMailConfigDto(
                batchConfig = BulkMailBatchConfigDto(
                    size = 50,
                    personalizedMails = true
                ),
                allBcc = true,
                delayedSend = now
            )
        )
    }

    @Test
    fun `create new bulk mail with defaults via mutate`() {

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
                                  "refs": [
                                    "alfresco/ray@bradbury",
                                    "$harryRef"
                                  ]
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
            BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("alfresco/ray@bradbury"),
                    harryRef
                )
            )
        )
        assertThat(bulkMail.config).isEqualTo(
            BulkMailConfigDto()
        )
    }

    @Test
    fun `check exists bulk mail payload`() {
        val toSave = BulkMailDto(
            id = null,
            name = "Bulk mail №123",
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("test@1"),
                    RecordRef.valueOf("alfresco/test@2")
                ),
                fromUserInput = "galina@mail.ru,vasya@mail.ru",
                custom = ObjectData.create(
                    """
                        {
                          "someCustom": [
                            "foo",
                            "bar"
                          ]
                        }
                    """.trimIndent()
                )
            ),
            record = docRef,
            template = testTemplateRef,
            type = NotificationType.EMAIL_NOTIFICATION,
            title = "Hello",
            body = "World",
            config = BulkMailConfigDto(
                batchConfig = BulkMailBatchConfigDto(
                    size = 50,
                ),
                delayedSend = Instant.parse("2011-12-14T08:30:00.0Z"),
                allBcc = true
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
                        "name": "name",
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
            .andExpect(jsonPath("$.id", `is`("notifications/bulk-mail@${saved.extId}")))
            .andExpect(jsonPath("$.attributes.title", `is`(saved.title)))
            .andExpect(jsonPath("$.attributes.body", `is`(saved.body)))
            .andExpect(jsonPath("$.attributes.status", `is`(saved.status)))
            .andExpect(jsonPath("$.attributes.name", `is`(saved.name)))
            .andExpect(jsonPath("$.attributes.type", `is`(saved.type.name)))
            .andExpect(jsonPath("$.attributes.record", `is`("Документ №123")))
            .andExpect(jsonPath("$.attributes.template", `is`("Test template")))
            .andExpect(jsonPath("$.attributes.recipientsData.refs[*]", Matchers.hasSize<Any>(2)))
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.refs[0]",
                    `is`(saved.recipientsData.refs[0].toString())
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.refs[1]",
                    `is`(saved.recipientsData.refs[1].toString())
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.fromUserInput",
                    `is`(saved.recipientsData.fromUserInput)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.custom.someCustom[*]",
                    Matchers.hasSize<Any>(2)
                )
            )
            .andExpect(jsonPath("$.attributes.recipientsData.custom.someCustom[0]", `is`("foo")))
            .andExpect(jsonPath("$.attributes.recipientsData.custom.someCustom[1]", `is`("bar")))
            .andExpect(
                jsonPath(
                    "$.attributes.config.batchConfig.size",
                    `is`(saved.config.batchConfig.size)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.config.batchConfig.personalizedMails",
                    `is`(saved.config.batchConfig.personalizedMails)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.config.delayedSend",
                    `is`(toSave.config.delayedSend!!.toString())
                )
            )
            .andExpect(jsonPath("$.attributes.config.allTo", `is`(saved.config.allTo)))
            .andExpect(jsonPath("$.attributes.config.allCc", `is`(saved.config.allCc)))
            .andExpect(jsonPath("$.attributes.config.allBcc", `is`(saved.config.allBcc)))
    }

    @Test
    fun `check exists bulk mail default payload`() {
        val toSave = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("test@1"),
                    RecordRef.valueOf("alfresco/test@2")
                )
            ),
            record = docRef,
            template = testTemplateRef,
            type = NotificationType.EMAIL_NOTIFICATION,
            title = "Hello",
            body = "World",
            config = BulkMailConfigDto(),
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
            .andExpect(jsonPath("$.id", `is`("notifications/bulk-mail@${saved.extId}")))
            .andExpect(jsonPath("$.attributes.title", `is`(saved.title)))
            .andExpect(jsonPath("$.attributes.body", `is`(saved.body)))
            .andExpect(jsonPath("$.attributes.status", `is`(saved.status)))
            .andExpect(jsonPath("$.attributes.type", `is`(saved.type.name)))
            .andExpect(jsonPath("$.attributes.record", `is`("Документ №123")))
            .andExpect(jsonPath("$.attributes.template", `is`("Test template")))
            .andExpect(jsonPath("$.attributes.recipientsData.refs[*]", Matchers.hasSize<Any>(2)))
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.refs[0]",
                    `is`(saved.recipientsData.refs[0].toString())
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.refs[1]",
                    `is`(saved.recipientsData.refs[1].toString())
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.fromUserInput",
                    `is`(saved.recipientsData.fromUserInput)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.recipientsData.custom[*]",
                    Matchers.hasSize<Any>(0)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.config.batchConfig.size",
                    `is`(saved.config.batchConfig.size)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.config.batchConfig.personalizedMails",
                    `is`(saved.config.batchConfig.personalizedMails)
                )
            )
            .andExpect(
                jsonPath(
                    "$.attributes.config.delayedSend",
                    `is`(nullValue())
                )
            )
            .andExpect(jsonPath("$.attributes.config.allTo", `is`(saved.config.allTo)))
            .andExpect(jsonPath("$.attributes.config.allCc", `is`(saved.config.allCc)))
            .andExpect(jsonPath("$.attributes.config.allBcc", `is`(saved.config.allBcc)))
    }

    @Test
    fun `update existing bulk mail via mutate`() {
        val savedExtId = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        RecordRef.valueOf("test@1"),
                        RecordRef.valueOf("alfresco/test@2")
                    )
                ),
                record = docRef,
                template = testTemplateRef,
                type = NotificationType.EMAIL_NOTIFICATION,
                title = "Hello",
                body = "World",
                config = BulkMailConfigDto(),
                status = "new"
            )
        ).extId

        val now = Instant.now()

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
                                  "refs": [
                                    "$harryRef",
                                    "$severusRef"
                                  ],
                                  "fromUserInput": "one,two",
                                  "custom": {
                                    "field0": 451,
                                    "field1": "Fahrenheit"
                                  }
                                },
                                "config": {
                                  "batchConfig": {
                                    "size": 50,
                                    "personalizedMails": true
                                  },
                                  "allBcc": true,
                                  "delayedSend": ${now.toEpochMilli()},
                                  "lang": "en"
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
            BulkMailRecipientsDataDto(
                refs = listOf(
                    harryRef,
                    severusRef
                ),
                fromUserInput = "one,two",
                custom = ObjectData.create(
                    """
                     {
                        "field0": 451,
                        "field1": "Fahrenheit"
                      }
                    """.trimIndent()
                )
            )
        )
        assertThat(updatedBulkMail.config).isEqualTo(
            BulkMailConfigDto(
                batchConfig = BulkMailBatchConfigDto(
                    size = 50,
                    personalizedMails = true
                ),
                allBcc = true,
                delayedSend = now,
                lang = Locale.ENGLISH
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
