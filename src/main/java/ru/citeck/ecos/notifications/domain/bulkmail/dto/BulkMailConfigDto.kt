package ru.citeck.ecos.notifications.domain.bulkmail.dto

import java.time.Instant
import java.util.*

/**
 * @author Roman Makarskiy
 */
data class BulkMailConfigDto(

    val batchConfig: BulkMailBatchConfigDto = BulkMailBatchConfigDto(),

    val delayedSend: Instant? = null,

    val allTo: Boolean = false,

    val allCc: Boolean = false,

    val allBcc: Boolean = false,

    val lang: Locale? = null

) {
    companion object

}
