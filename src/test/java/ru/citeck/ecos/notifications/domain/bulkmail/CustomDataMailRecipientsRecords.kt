package ru.citeck.ecos.notifications.domain.bulkmail

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.bulkmail.service.RecipientInfo
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class CustomDataMailRecipientsRecords : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "custom-mail-recipients"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Result {
        val generateSize = recsQuery.query.get("generateSize").asInt()
        val prefix = recsQuery.query.get("prefix").asText()

        return Result(
            generateSize, prefix
        )
    }

    open inner class Result(
        private val generateSize: Int = 0,
        private val prefix: String
    ) {

        @get:AttName("recipients")
        val recipients: List<RecipientInfo>
            get() = let {
                val result = arrayListOf<RecipientInfo>()

                for (i in 1..generateSize) {
                    result.add(RecipientInfo(
                        address = "${prefix}_$i@mail.ru"
                    ))
                }

                return result
            }

    }
}
