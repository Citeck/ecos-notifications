package ru.citeck.ecos.notifications.domain.bulkmail.dto

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.webapp.api.entity.EntityRef

/**
 * @author Roman Makarskiy
 */
data class BulkMailRecipientsDataDto(

    val refs: List<EntityRef> = emptyList(),

    val fromUserInput: String = "",

    val custom: ObjectData = ObjectData.create()

) {
    companion object
}
