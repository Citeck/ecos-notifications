package ru.citeck.ecos.notifications.domain.bulkmail

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.zookeeper.ZkConfigService
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.RecipientsFinder
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.util.concurrent.TimeUnit

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
class RecipientsFinderTest {


    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var recipientsFinder: RecipientsFinder

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var zkConfigService: ZkConfigService

    companion object {
        private val harryRef = RecordRef.valueOf("alfresco/people@harry")
        private val severusRef = RecordRef.valueOf("alfresco/people@severus")

        private val harryRecord = PotterRecord()
        private val severusRecord = SnapeRecord()
    }

    @Before
    fun setUp() {
        localAppService.deployLocalArtifacts()

        recordsService.register(
            RecordsDaoBuilder.create("alfresco/people")
                .addRecord(
                    harryRef.id,
                    harryRecord
                )
                .addRecord(
                    severusRef.id,
                    severusRecord
                )
                .build()
        )
    }

    @Test
    fun `get recipients from recipients user refs`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                recipients = listOf(
                    harryRef,
                    severusRef
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail)

        assertThat(recipients.size).isEqualTo(2)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                harryRecord.email,
                severusRecord.email
            )

    }

    @Test
    fun `parse recipients from user input emails and user names`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                fromUserInput = ";;, test1@mail.ru test2@mail.ru,test3@mail.ru;test4@mail.ru,test5@mail.ru" +
                    "\nharry\n\n severus someNonExistsUser"
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail)

        assertThat(recipients.size).isEqualTo(7)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                "test1@mail.ru",
                "test2@mail.ru",
                "test3@mail.ru",
                "test4@mail.ru",
                "test5@mail.ru",
                harryRecord.email,
                severusRecord.email
            )

    }

    @Test
    fun `get recipients from custom`() {
        setProviders(listOf("notifications/custom-fixed-recipients", "notifications/custom-mail-recipients"))

        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                custom = ObjectData.create(
                    """
                     {
                        "generateSize": 3,
                        "prefix": "user"
                      }
                """.trimIndent()
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail)

        assertThat(recipients.size).isEqualTo(5)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                "user_1@mail.ru",
                "user_2@mail.ru",
                "user_3@mail.ru",
                "recipient_1",
                "recipient_2"
            )

        setProviders(emptyList())
    }

    private fun setProviders(providers: List<String>) {
        zkConfigService.setConfig(
            ConfigKey(
                id = "bulk-mail-custom-recipients-providers",
                scope = "app/notifications"
            ), providers, 2
        )

        TimeUnit.SECONDS.sleep(1)
    }

    class PotterRecord(

        @AttName("email")
        val email: String = "harry.potter@hogwarts.com"

    )

    class SnapeRecord(

        @AttName("email")
        val email: String = "severus.snape@hogwarts.com"

    )

}
