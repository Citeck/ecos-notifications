package ru.citeck.ecos.notifications.domain.bulkmail

import org.springframework.stereotype.Component
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
        val recipients: List<String>
            get() = listOf("recipient_1", "recipient_2")

    }
}
