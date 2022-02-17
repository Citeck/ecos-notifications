package ru.citeck.ecos.notifications.domain.bulkmail.service

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.notifications.domain.bulkmail.converter.isAuthorityGroupRef
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.util.regex.Pattern

/**
 * @author Roman Makarskiy
 */
@Component
class RecipientsFinder(
    private val recordsService: RecordsService
) {

    private val emailPattern = Pattern.compile("^.+@.+\\..+$")
    private val recipientsAttribute = "recipients[]"

    @EcosConfig("bulk-mail-custom-recipients-providers")
    var customProviders: List<String> = emptyList()

    /**
     * In the current implementation returns only user emails. <br>
     * TODO: implement getting recipients for different {@link ru.citeck.ecos.notifications.lib.NotificationType}
     *
     * @return recipients emails
     */
    fun resolveRecipients(bulkMail: BulkMailDto): Set<String> {
        val result = mutableSetOf<String>()

        result.addAll(getRecipientsFromRefs(bulkMail.recipientsData.recipients))
        result.addAll(getRecipientsFromUserInput(bulkMail.recipientsData.fromUserInput))
        result.addAll(getRecipientsFromCustom(bulkMail.recipientsData.custom))

        return result
    }

    private fun getRecipientsFromRefs(refs: List<RecordRef>): List<String> {
        val allUsers = mutableSetOf<RecordRef>()
        val groups = mutableListOf<RecordRef>()

        refs.forEach {
            if (it.isAuthorityGroupRef()) groups.add(it) else allUsers.add(it)
        }

        val usersFromGroup = recordsService.getAtts(groups, GroupInfo::class.java).map { it.containedUsers }.flatten()
        allUsers.addAll(usersFromGroup)

        //TODO: remove
        allUsers.forEach {
            val r = recordsService.getAtt(it, "?json")
            println("----------------------")
            println("$it:")
            println(r)
            println("----------------------")
        }

        return recordsService.getAtts(allUsers, UserInfo::class.java).mapNotNull { it.email }
    }

    private fun getRecipientsFromUserInput(input: String): List<String> {
        val splitInput = Splitter.on(CharMatcher.anyOf(",;\n "))
            .trimResults()
            .omitEmptyStrings()
            .split(input).toList()

        val userRefs = arrayListOf<RecordRef>()
        val users = arrayListOf<String>()

        splitInput.filter {
            !emailPattern.matcher(it).matches()
        }.forEach {
            users.add(it)
            userRefs.add(RecordRef.create("alfresco", "people", it))
        }

        val emails = recordsService.getAtts(userRefs, UserInfo::class.java).mapNotNull { it.email }

        val result = splitInput.toMutableList()
        result.removeAll(users)
        result.addAll(emails)

        return result
    }

    private fun getRecipientsFromCustom(data: ObjectData): List<String> {
        val result = mutableListOf<String>()

        customProviders.forEach { provider ->

            val query = RecordsQuery.Builder()
                .withQuery(data)
                .withSourceId(provider)
                .withMaxItems(1)
                .build()

            val found = recordsService.queryOne(query, recipientsAttribute).asList(String::class.java)
            result.addAll(found)
        }

        return result
    }

}

data class UserInfo(
    @AttName("email")
    var email: String? = ""
)

data class GroupInfo(
    @AttName("containedUsers")
    val containedUsers: List<RecordRef> = emptyList()
)
