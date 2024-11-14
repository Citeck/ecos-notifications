package ru.citeck.ecos.notifications.domain.bulkmail.service

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.notifications.domain.bulkmail.converter.from
import ru.citeck.ecos.notifications.domain.bulkmail.converter.isAuthorityGroupRef
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailRecipientDto
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.regex.Pattern

private const val AUTHORITY_GROUP_SRC_ID = "authority-group"
private const val PERSON_SRC_ID = "person"
private const val WORKSPACE_PREFIX = "workspace://"

/**
 * @author Roman Makarskiy
 */
@Component
class RecipientsFinder(
    private val recordsService: RecordsService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi
) {

    private val emailPattern = Pattern.compile("^.+@.+\\..+$")
    private val recipientsAttribute = "recipients[]?json"
    private val emptyObjectData = ObjectData.create()

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
        val authorityRefs = convertRecipientsToFullFilledRefs(bulkMail)

        val allUsers = mutableSetOf<EntityRef>()
        val groups = mutableListOf<EntityRef>()

        authorityRefs.forEach {
            if (it.isAuthorityGroupRef()) groups.add(it) else allUsers.add(it)
        }

        val usersFromGroup =
            recordsService.getAtts(groups, GroupInfo::class.java).map { it.containedUsers }.flatten()
        allUsers.addAll(usersFromGroup)

        return recordsService.getAtts(allUsers, UserInfo::class.java)
            .filter { activeUserFilter(it) }
            .map { BulkMailRecipientDto.from(it, bulkMail.recordRef) }
    }

    private fun convertRecipientsToFullFilledRefs(bulkMail: BulkMailDto): List<EntityRef> {
        val authorityIds = bulkMail.recipientsData.refs
            .filter { it.getLocalId().startsWith(WORKSPACE_PREFIX).not() }

        return ecosAuthoritiesApi.getAuthorityRefs(authorityIds)
    }

    private fun getRecipientsFromUserInput(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        val userRefs = mutableListOf<EntityRef>()
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
                    userRefs.add(ecosAuthoritiesApi.getAuthorityRef(it))
                }
            }

        val result = mutableListOf<BulkMailRecipientDto>()

        emails.forEach { email ->
            result.add(BulkMailRecipientDto.from(email, bulkMail.recordRef))
        }

        recordsService.getAtts(userRefs, UserInfo::class.java)
            .filter { activeUserFilter(it) }
            .forEach { userInfo ->
                result.add(BulkMailRecipientDto.from(userInfo, bulkMail.recordRef))
            }

        return result
    }

    private fun activeUserFilter(user: UserInfo) = (user.disabled != true) && (user.email?.isNotBlank() ?: false)

    private fun getRecipientsFromCustom(bulkMail: BulkMailDto): List<BulkMailRecipientDto> {
        if (bulkMail.recipientsData.custom.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<BulkMailRecipientDto>()
        if (emptyObjectData == bulkMail.recipientsData.custom) {
            return result
        }

        customProviders.forEach { provider ->

            val query = RecordsQuery.Builder()
                .withQuery(bulkMail.recipientsData.custom)
                .withSourceId(provider)
                .withMaxItems(1)
                .build()

            val found = recordsService.queryOne(query, recipientsAttribute).asList(RecipientInfo::class.java)
                .filter { it.address?.isNotBlank() == true }
                .map { BulkMailRecipientDto.from(it, bulkMail.recordRef) }

            result.addAll(found)
        }

        return result
    }
}

data class UserInfo(
    @AttName("personDisabled")
    var disabled: Boolean? = false,

    @AttName("email")
    var email: String? = "",

    @AttName(".disp")
    var disp: String? = "",

    @AttName(".id")
    var record: EntityRef? = EntityRef.EMPTY
)

data class GroupInfo(
    @AttName("containedUsers")
    val containedUsers: List<EntityRef> = emptyList()
)

data class RecipientInfo(
    @AttName("address")
    var address: String? = "",

    @AttName(".disp")
    var disp: String? = "",

    @AttName("record")
    var record: EntityRef? = EntityRef.EMPTY

)
