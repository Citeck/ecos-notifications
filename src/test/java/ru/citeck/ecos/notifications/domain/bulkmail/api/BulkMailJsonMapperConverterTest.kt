package ru.citeck.ecos.notifications.domain.bulkmail.api

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailBatchConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailConfigDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientsDataDto
import ru.citeck.ecos.records2.RecordRef
import java.time.Instant

/**
 * @author Roman Makarskiy
 */
class BulkMailJsonMapperConverterTest {

    @Test
    fun `bulk mail config json converter test`() {
        val config = BulkMailConfigDto(
            batchConfig = BulkMailBatchConfigDto(
                size = 50,
            ),
            delayedSend = Instant.parse("2011-12-14T08:30:00.0Z"),
            allBcc = true
        )

        val json = Json.mapper.toString(config)

        val dtoReaded = Json.mapper.read(json, BulkMailConfigDto::class.java)

        Assertions.assertThat(dtoReaded).isEqualTo(config)
    }

    @Test
    fun `bulk mail recipients data json converter test`() {
        val recipients = BulkMailRecipientsDataDto(
            refs = listOf(RecordRef.valueOf("test@1"), RecordRef.valueOf("test@2")),
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
        )

        val json = Json.mapper.toString(recipients)

        Assertions.assertThat(Json.mapper.read(json, BulkMailRecipientsDataDto::class.java)).isEqualTo(recipients)
    }
}
