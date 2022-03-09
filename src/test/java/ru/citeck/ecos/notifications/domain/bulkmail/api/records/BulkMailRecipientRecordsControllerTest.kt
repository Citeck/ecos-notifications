package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailRecipientDao
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.web.rest.TestUtil
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.spring.config.RecordsServiceFactoryConfiguration
import ru.citeck.ecos.records3.spring.web.rest.RecordsRestApi
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class BulkMailRecipientRecordsControllerTest {

    companion object {
        private val docRef = RecordRef.valueOf("doc@test-document")
        private val docRecord = DocDto()
    }

    @Autowired
    private lateinit var factoryConfig: RecordsServiceFactoryConfiguration

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var bulkMailRecipientDao: BulkMailRecipientDao

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    private lateinit var mockRecordsApi: MockMvc

    @Before
    fun setUp() {
        val recordsApi = RecordsRestApi(factoryConfig)
        this.mockRecordsApi = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build()

        recordsService.register(
            RecordsDaoBuilder.create(docRef.sourceId)
                .addRecord(docRef.id, docRecord)
                .build()
        )
    }


    @Test
    fun `check exists bulk mail recipient payload`() {
        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                recipientsData = BulkMailRecipientsDataDto(),
                type = NotificationType.EMAIL_NOTIFICATION,
            )
        )

        val saved = BulkMailRecipientDto(
            extId = "test-id",
            record = docRef,
            address = "test@mail.ru",
            name = "Nano Pro"
        )

        bulkMailRecipientDao.saveForBulkMail(saved, bulkMail)

        mockRecordsApi.perform(
            MockMvcRequestBuilders.post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(
                    """
                    {
                      "record": "notifications/bulk-mail-recipient@${saved.extId}",
                      "attributes": {
                        "address": "address",
                        "disp": ".disp",
                        "record": "record",
                        "bulkMail": "bulkMailRef?id"
                      }
                    }
                """.trimIndent()
                )
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                jsonPath(
                    "$.id", Matchers.`is`("bulk-mail-recipient@${saved.extId}")
                )
            )
            .andExpect(jsonPath("$.attributes.address", Matchers.`is`(saved.address)))
            .andExpect(jsonPath("$.attributes.disp", Matchers.`is`(saved.name)))
            .andExpect(jsonPath("$.attributes.record", Matchers.`is`(docRecord.name)))
            .andExpect(jsonPath("$.attributes.bulkMail",
                Matchers.`is`("notifications/bulk-mail@${bulkMail.extId}"))
            )

    }

    class DocDto(
        @AttName("?disp")
        val name: String = "Документ №123"
    )

}
