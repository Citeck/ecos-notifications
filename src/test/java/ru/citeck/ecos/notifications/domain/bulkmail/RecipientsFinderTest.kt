package ru.citeck.ecos.notifications.domain.bulkmail

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.provider.InMemConfigProvider
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.converter.from
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.RecipientsFinder
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * @author Roman Makarskiy
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class RecipientsFinderTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var recipientsFinder: RecipientsFinder

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var inMemConfigService: InMemConfigProvider

    companion object {
        private val harryRef = RecordRef.valueOf("alfresco/people@harry")
        private val severusRef = RecordRef.valueOf("alfresco/people@severus")
        private val hogwartsRef = RecordRef.valueOf("alfresco/authority@GROUP_hogwarts")

        private val harryRecord = PotterRecord()
        private val severusRecord = SnapeRecord()
        private val hogwartsRecord = HogwartsRecord()
    }

    @BeforeEach
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

        recordsService.register(
            RecordsDaoBuilder.create("alfresco/authority")
                .addRecord(
                    hogwartsRef.id,
                    hogwartsRecord
                )
                .build()
        )
    }

    @Test
    fun `get recipients from recipients user refs`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    harryRef,
                    severusRef
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(2)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = harryRecord.email,
                        name = harryRecord.name,
                        record = harryRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = severusRecord.email,
                        name = severusRecord.name,
                        record = severusRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                )
            )
    }

    @Test
    fun `get recipients from recipients refs with converted to full group refs`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("GROUP_hogwarts")
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(2)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = harryRecord.email,
                        name = harryRecord.name,
                        record = harryRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = severusRecord.email,
                        name = severusRecord.name,
                        record = severusRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                )
            )
    }

    @Test
    fun `get recipients from recipients refs with converted to full users refs`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("harry"),
                    RecordRef.valueOf("severus")
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(2)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = harryRecord.email,
                        name = harryRecord.name,
                        record = harryRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = severusRecord.email,
                        name = severusRecord.name,
                        record = severusRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                )
            )
    }

    @Test
    fun `get recipients from recipients refs with converted to full users and group refs`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    RecordRef.valueOf("harry"),
                    RecordRef.valueOf("severus"),
                    RecordRef.valueOf("GROUP_hogwarts")
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(2)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = harryRecord.email,
                        name = harryRecord.name,
                        record = harryRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = severusRecord.email,
                        name = severusRecord.name,
                        record = severusRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                )
            )
    }

    @Test
    fun `get recipients from recipients group refs`() {
        val bulkMail = BulkMailDto(
            id = null,
            recipientsData = BulkMailRecipientsDataDto(
                refs = listOf(
                    hogwartsRef
                )
            ),
            type = NotificationType.EMAIL_NOTIFICATION,
        )

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(2)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = harryRecord.email,
                        name = harryRecord.name,
                        record = harryRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = severusRecord.email,
                        name = severusRecord.name,
                        record = severusRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                )
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

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(7)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(BulkMailRecipientDto.from("test1@mail.ru", bulkMail.recordRef)),
                RecipientsEqualsWrapper(BulkMailRecipientDto.from("test2@mail.ru", bulkMail.recordRef)),
                RecipientsEqualsWrapper(BulkMailRecipientDto.from("test3@mail.ru", bulkMail.recordRef)),
                RecipientsEqualsWrapper(BulkMailRecipientDto.from("test4@mail.ru", bulkMail.recordRef)),
                RecipientsEqualsWrapper(BulkMailRecipientDto.from("test5@mail.ru", bulkMail.recordRef)),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = harryRecord.email,
                        name = harryRecord.name,
                        record = harryRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = severusRecord.email,
                        name = severusRecord.name,
                        record = severusRef,
                        bulkMailRef = bulkMail.recordRef
                    )
                )
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

        val recipients = recipientsFinder.resolveRecipients(bulkMail).map { RecipientsEqualsWrapper(it) }

        assertThat(recipients.size).isEqualTo(5)
        assertThat(recipients)
            .containsExactlyInAnyOrder(
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = "user_1@mail.ru",
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = "user_2@mail.ru",
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = "user_3@mail.ru",
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = "recipient_1@mail.ru",
                        name = "Recipient 1",
                        record = RecordRef.valueOf("rec@1"),
                        bulkMailRef = bulkMail.recordRef
                    )
                ),
                RecipientsEqualsWrapper(
                    BulkMailRecipientDto(
                        address = "recipient_2@mail.ru",
                        bulkMailRef = bulkMail.recordRef
                    )
                )
            )

        setProviders(emptyList())
    }

    private fun setProviders(providers: List<String>) {
        inMemConfigService.setConfig("bulk-mail-custom-recipients-providers", providers)
    }

    class PotterRecord(

        @AttName("email")
        val email: String = "harry.potter@hogwarts.com",

        @AttName(".disp")
        val name: String = "Harry Potter",

        @AttName(".id")
        val id: RecordRef = harryRef

    )

    class SnapeRecord(

        @AttName("email")
        val email: String = "severus.snape@hogwarts.com",

        @AttName(".disp")
        val name: String = "Severus Snape",

        @AttName(".id")
        val id: RecordRef = severusRef
    )

    class HogwartsRecord(

        @AttName("containedUsers")
        val users: List<RecordRef> = listOf(harryRef, severusRef)

    )

    data class RecipientsEqualsWrapper(
        val dto: BulkMailRecipientDto
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RecipientsEqualsWrapper

            if (dto.address != other.dto.address) return false
            if (dto.name != other.dto.name) return false
            if (dto.record != other.dto.record) return false
            if (dto.bulkMailRef != other.dto.bulkMailRef) return false

            return true
        }

        override fun hashCode(): Int {
            var result = dto.address.hashCode()
            result = 31 * result + dto.name.hashCode()
            result = 31 * result + dto.record.hashCode()
            result = 31 * result + dto.bulkMailRef.hashCode()
            return result
        }
    }
}
