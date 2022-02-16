package ru.citeck.ecos.notifications.domain.bulkmail.service

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName

/**
 * @author Roman Makarskiy
 */
@Component
class RecipientsFinder(
    private val recordsService: RecordsService
) {

    fun resolveRecipients(bulkMail: BulkMailDto): Set<String> {
        val result = mutableSetOf<String>()

        result.addAll(getRecipientsFromRefs(bulkMail.recipientsData.recipients))
        result.addAll(getRecipientsFromUserInput(bulkMail.recipientsData.fromUserInput))
        result.addAll(getRecipientsFromCustom(bulkMail.recipientsData.custom))

        return result
    }

    private fun getRecipientsFromRefs(refs: List<RecordRef>): List<String> {
        //TODO: remove
        refs.forEach {
            val r = recordsService.getAtt(it, "?json")
            println("----------------------")
            println("$it:")
            println(r)
            println("----------------------")
        }

        return recordsService.getAtts(refs, UserInfo::class.java).mapNotNull { it.email }
    }

    private fun getRecipientsFromUserInput(input: String): List<String> {
        return Splitter.on(CharMatcher.anyOf(",;\n "))
            .trimResults()
            .omitEmptyStrings()
            .split(input).toList()
    }

    //TODO: implement
    private fun getRecipientsFromCustom(data: ObjectData): List<String> {
        return emptyList()
    }

}

data class UserInfo(
    @AttName("email")
    var email: String? = ""
)
