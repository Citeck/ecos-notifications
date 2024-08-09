package ru.citeck.ecos.notifications.domain.bulkmail.api.records

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailRecipientDao
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [ru.citeck.ecos.notifications.NotificationsApp::class])
class BulkMailRecipientRecordsControllerTest {

    companion object {
        private val docRef = EntityRef.valueOf("doc@test-document")
        private val docRecord = DocDto()
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var bulkMailRecipientDao: BulkMailRecipientDao

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    @BeforeEach
    fun setUp() {
        recordsService.register(
            RecordsDaoBuilder.create(docRef.getSourceId())
                .addRecord(docRef.getLocalId(), docRecord)
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

        val res = recordsService.getAtts(
            "notifications/bulk-mail-recipient@${saved.extId}",
            mapOf(
                "address" to "address",
                "disp" to ".disp",
                "record" to "record",
                "bulkMail" to "bulkMailRef?id"
            )
        )
        assertThat(res.getId().toString()).isEqualTo("notifications/bulk-mail-recipient@${saved.extId}")
        assertThat(res.getAtt("address").asText()).isEqualTo(saved.address)
        assertThat(res.getAtt("disp").asText()).isEqualTo(saved.name)
        assertThat(res.getAtt("record").asText()).isEqualTo(docRecord.name)
        assertThat(res.getAtt("bulkMail").asText()).isEqualTo("notifications/bulk-mail@${bulkMail.extId}")
    }

    class DocDto(
        @AttName("?disp")
        val name: String = "Документ №123"
    )
}
