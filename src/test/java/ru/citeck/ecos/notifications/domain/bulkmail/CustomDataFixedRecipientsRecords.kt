package ru.citeck.ecos.notifications.domain.bulkmail

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.bulkmail.service.RecipientInfo
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class CustomDataFixedRecipientsRecords : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "custom-fixed-recipients"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Result {
        return Result()
    }

    open inner class Result {

        @get:AttName("recipients")
        val recipients: List<RecipientInfo>
            get() = listOf(
                RecipientInfo(
                    address = "recipient_1@mail.ru",
                    disp = "Recipient 1",
                    record = RecordRef.valueOf("rec@1")
                ),
                RecipientInfo(
                    address = "recipient_2@mail.ru"
                )
            )

    }
}
