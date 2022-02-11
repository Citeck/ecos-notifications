package ru.citeck.ecos.notifications.domain.bulkmail.dto

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records2.RecordRef

/**
 * @author Roman Makarskiy
 */
data class BulkMailRecipientsDataDto(

    val recipients: List<RecordRef> = emptyList(),

    val fromUserInput: String = "",

    val custom: ObjectData = ObjectData.create()

) {
    companion object
}
