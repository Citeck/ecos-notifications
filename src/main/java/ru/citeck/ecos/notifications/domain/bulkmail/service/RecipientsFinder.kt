package ru.citeck.ecos.notifications.domain.bulkmail.service

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.springframework.stereotype.Component
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.notifications.domain.bulkmail.converter.*
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.util.regex.Pattern

private const val ALFRESCO_APP = "alfresco"
private const val AUTHORITY_SRC_ID = "authority"
private const val PEOPLE_SRC_ID = "people"
private const val WORKSPACE_PREFIX = "workspace://"

/**
 * @author Roman Makarskiy
 */
@Component
class RecipientsFinder(
    private val recordsService: RecordsService
) {

    private val emailPattern = Pattern.compile("^.+@.+\\..+$")
    private val recipientsAttribute = "recipients[]?json"

    @EcosConfig("bulk-mail-custom-recipients-providers")
    var customProviders: List<String> = emptyList()

    /**
     * In the current implementation returns only user emails. <br>
     * TODO: implement getting recipients for different {@link ru.citeck.ecos.notifications.lib.NotificationType}
     *
     * @return recipients emails
     */
    fun resolveRecipients(bulkMail: BulkMailDto): Set<BulkMailRecipientDto> {
        val result = mutableSetOf<BulkMailRecipientDto>()

        result.addAll(getRecipientsFromRefs(bulkMail))
        result.addAll(getRecipientsFromUserInput(bulkMail))
        result.addAll(getRecipientsFromCustom(bulkMail))

        return result
    }

    private fun getRecipientsFromRefs(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        val convertedRefs = convertRecipientsToFullFilledRefs(bulkMail.recipientsData.refs)

        val allUsers = mutableSetOf<RecordRef>()
        val groups = mutableListOf<RecordRef>()

        convertedRefs.forEach {
            if (it.isAuthorityGroupRef()) groups.add(it) else allUsers.add(it)
        }

        val usersFromGroup = recordsService.getAtts(groups, GroupInfo::class.java).map { it.containedUsers }.flatten()
        allUsers.addAll(usersFromGroup)

        return recordsService.getAtts(allUsers, UserInfo::class.java)
            .filter { it.email?.isNotBlank() ?: false }
            .map { BulkMailRecipientDto.from(it, bulkMail.recordRef) }
    }

    /**
     * Select orgstruct component send to backend userName or groupName. We need convert it to full recordRef format.
     * TODO: migrate to model (microservice) people/groups after completion of development.
     */
    private fun convertRecipientsToFullFilledRefs(recipients: List<RecordRef>): List<RecordRef> {
        return recipients.map {
            if (it.id.startsWith(WORKSPACE_PREFIX)) {
                throw IllegalArgumentException("NodeRef format does not support. Recipient: $it")
            }

            var fullFilledRef = it

            if (fullFilledRef.appName.isBlank()) {
                fullFilledRef = fullFilledRef.addAppName(ALFRESCO_APP)
            }

            if (fullFilledRef.sourceId.isBlank()) {
                val sourceId = if (fullFilledRef.isAuthorityGroupRef()) AUTHORITY_SRC_ID else PEOPLE_SRC_ID
                fullFilledRef = fullFilledRef.withSourceId(sourceId)
            }

            fullFilledRef
        }
    }

    private fun getRecipientsFromUserInput(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        val userRefs = mutableListOf<RecordRef>()
        val emails = mutableListOf<String>()

        Splitter.on(CharMatcher.anyOf(",;\n "))
            .trimResults()
            .omitEmptyStrings()
            .split(bulkMail.recipientsData.fromUserInput)
            .forEach {
                val isEmail = emailPattern.matcher(it).matches()

                if (isEmail) {
                    emails.add(it)
                } else {
                    userRefs.add(RecordRef.create("alfresco", "people", it))
                }
            }

        val result = mutableListOf<BulkMailRecipientDto>()

        emails.forEach { email ->
            result.add(BulkMailRecipientDto.from(email, bulkMail.recordRef))
        }

        recordsService.getAtts(userRefs, UserInfo::class.java)
            .filter { it.email?.isNotBlank() ?: false }
            .forEach { userInfo ->
                result.add(BulkMailRecipientDto.from(userInfo, bulkMail.recordRef))
            }

        return result
    }

    private fun getRecipientsFromCustom(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        val result = mutableListOf<BulkMailRecipientDto>()

        customProviders.forEach { provider ->

            val query = RecordsQuery.Builder()
                .withQuery(bulkMail.recipientsData.custom)
                .withSourceId(provider)
                .withMaxItems(1)
                .build()

            val found = recordsService.queryOne(query, recipientsAttribute).asList(RecipientInfo::class.java)
                .filter { it.address?.isNotBlank() ?: false }
                .map { BulkMailRecipientDto.from(it, bulkMail.recordRef) }

            result.addAll(found)
        }

        return result
    }

}

data class UserInfo(
    @AttName("email")
    var email: String? = "",

    @AttName(".disp")
    var disp: String? = "",

    @AttName(".id")
    var record: RecordRef? = RecordRef.EMPTY
)

data class GroupInfo(
    @AttName("containedUsers")
    val containedUsers: List<RecordRef> = emptyList()
)

data class RecipientInfo(

    @AttName("address")
    var address: String? = "",


    @AttName(".disp")
    var disp: String? = "",

    @AttName("record")
    var record: RecordRef? = RecordRef.EMPTY

)
