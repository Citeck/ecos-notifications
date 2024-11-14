package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.DataValue
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
import ru.citeck.ecos.notifications.stringFromResource
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
class BulkMailRecordsControllerTest {

    companion object {
        private val testTemplateRef = EntityRef.create("notifications", "template", "test-template")
        private val docRef = EntityRef.valueOf("doc@test-document")

        private val harryRef = EntityRef.valueOf("alfresco/people@harry")
        private val severusRef = EntityRef.valueOf("alfresco/people@severus")

        private val departmentGroupRef = EntityRef.valueOf("alfresco/authority@GROUP_department")
    }

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var recordsService: RecordsService

    @BeforeEach
    fun setUp() {

        val notificationTemplate = Json.mapper.convert(
            stringFromResource("template/test-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create(docRef.getSourceId())
                .addRecord(docRef.getLocalId(), DocDto())
                .build()
        )
    }

    @Test
    fun `create new bulk mail via mutate`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val records = DataValue.of(
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
        )["records"].asList(RecordAtts::class.java)

        val result = recordsService.mutate(records)

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
                    EntityRef.valueOf("alfresco/ray@bradbury"),
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

        val records = DataValue.of(
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
        )["records"].asList(RecordAtts::class.java)

        val result = recordsService.mutate(records)

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
                    EntityRef.valueOf("alfresco/ray@bradbury"),
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
                    EntityRef.valueOf("test@1"),
                    EntityRef.valueOf("alfresco/test@2")
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

        val getAttsRes = recordsService.getAtts(
            "notifications/bulk-mail@${saved.extId}",
            mapOf(
                "title" to "title",
                "body" to "body",
                "name" to "name",
                "status" to "status",
                "record" to "record",
                "type" to "type",
                "template" to "template",
                "recipientsData" to "recipientsData?json",
                "config" to "config?json"
            )
        )
        assertThat(getAttsRes.getId().toString()).isEqualTo("notifications/bulk-mail@${saved.extId}")
        assertThat(getAttsRes["title"].asText()).isEqualTo(saved.title)
        assertThat(getAttsRes["body"].asText()).isEqualTo(saved.body)
        assertThat(getAttsRes["status"].asText()).isEqualTo(saved.status)
        assertThat(getAttsRes["name"].asText()).isEqualTo(saved.name)
        assertThat(getAttsRes["type"].asText()).isEqualTo(saved.type.name)
        assertThat(getAttsRes["record"].asText()).isEqualTo("Документ №123")
        assertThat(getAttsRes["template"].asText()).isEqualTo("Test template")
        assertThat(getAttsRes["$.recipientsData.refs[*]"].asStrList()).hasSize(2)
        assertThat(getAttsRes["$.recipientsData.refs[0]"].asText()).isEqualTo(saved.recipientsData.refs[0].toString())
        assertThat(getAttsRes["$.recipientsData.refs[1]"].asText()).isEqualTo(saved.recipientsData.refs[1].toString())
        assertThat(getAttsRes["$.recipientsData.fromUserInput"].asText()).isEqualTo(saved.recipientsData.fromUserInput)
        assertThat(getAttsRes["$.recipientsData.custom.someCustom[*]"].asStrList()).hasSize(2)
        assertThat(getAttsRes["$.recipientsData.custom.someCustom[0]"].asText()).isEqualTo("foo")
        assertThat(getAttsRes["$.recipientsData.custom.someCustom[1]"].asText()).isEqualTo("bar")
        assertThat(getAttsRes["$.config.batchConfig.size"].asInt()).isEqualTo(saved.config.batchConfig.size)
        assertThat(getAttsRes["$.config.batchConfig.personalizedMails"].asBoolean()).isEqualTo(saved.config.batchConfig.personalizedMails)
        assertThat(getAttsRes["$.config.delayedSend"].getAsInstant()).isEqualTo(toSave.config.delayedSend!!.toString())
        assertThat(getAttsRes["$.config.allTo"].asBoolean()).isEqualTo(saved.config.allTo)
        assertThat(getAttsRes["$.config.allCc"].asBoolean()).isEqualTo(saved.config.allCc)
        assertThat(getAttsRes["$.config.allBcc"].asBoolean()).isEqualTo(saved.config.allBcc)
    }

    @Test
    fun `check exists bulk mail default payload`() {
        val toSave = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    EntityRef.valueOf("test@1"),
                    EntityRef.valueOf("alfresco/test@2")
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

        val getAttsRes = recordsService.getAtts(
            "notifications/bulk-mail@${saved.extId}",
            mapOf(
                "title" to "title",
                "body" to "body",
                "status" to "status",
                "record" to "record",
                "type" to "type",
                "template" to "template",
                "recipientsData" to "recipientsData?json",
                "config" to "config?json"
            )
        )

        assertThat(getAttsRes.getId().toString()).isEqualTo("notifications/bulk-mail@${saved.extId}")
        assertThat(getAttsRes["title"].asText()).isEqualTo(saved.title)
        assertThat(getAttsRes["body"].asText()).isEqualTo(saved.body)
        assertThat(getAttsRes["status"].asText()).isEqualTo(saved.status)
        assertThat(getAttsRes["type"].asText()).isEqualTo(saved.type.name)
        assertThat(getAttsRes["record"].asText()).isEqualTo("Документ №123")
        assertThat(getAttsRes["template"].asText()).isEqualTo("Test template")
        assertThat(getAttsRes["$.recipientsData.refs[*]"].asStrList()).hasSize(2)
        assertThat(getAttsRes["$.recipientsData.refs[0]"].asText()).isEqualTo(saved.recipientsData.refs[0].toString())
        assertThat(getAttsRes["$.recipientsData.refs[1]"].asText()).isEqualTo(saved.recipientsData.refs[1].toString())
        assertThat(getAttsRes["$.recipientsData.fromUserInput"].asText()).isEqualTo(saved.recipientsData.fromUserInput)
        assertThat(getAttsRes["$.recipientsData.custom[*]"].asStrList()).isEmpty()
        assertThat(getAttsRes["$.config.batchConfig.size"].asInt()).isEqualTo(saved.config.batchConfig.size)
        assertThat(getAttsRes["$.config.batchConfig.personalizedMails"].asBoolean()).isEqualTo(saved.config.batchConfig.personalizedMails)
        assertThat(getAttsRes["$.config.delayedSend"].getAsInstant()).isNull()
        assertThat(getAttsRes["$.config.allTo"].asBoolean()).isEqualTo(saved.config.allTo)
        assertThat(getAttsRes["$.config.allCc"].asBoolean()).isEqualTo(saved.config.allCc)
        assertThat(getAttsRes["$.config.allBcc"].asBoolean()).isEqualTo(saved.config.allBcc)
    }

    @Test
    fun `update existing bulk mail via mutate`() {
        val savedExtId = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(
                    refs = listOf(
                        EntityRef.valueOf("test@1"),
                        EntityRef.valueOf("alfresco/test@2")
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

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val records = DataValue.of(
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
        )["records"].asList(RecordAtts::class.java)

        val result = recordsService.mutate(records)

        val updatedBulkMail = getBulkMailFromResponse(result)

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

    private fun getBulkMailFromResponse(result: List<EntityRef>): BulkMailDto {
        return bulkMailDao.findByExtId(result.first().getLocalId())!!
    }

    class DocDto(
        @AttName("?disp")
        val name: String = "Документ №123"
    )
}
