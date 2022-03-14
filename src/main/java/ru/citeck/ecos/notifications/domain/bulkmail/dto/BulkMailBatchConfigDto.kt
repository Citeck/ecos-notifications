package ru.citeck.ecos.notifications.domain.bulkmail.dto

/**
 * @author Roman.Makarskiy
 *
 * Used to generate several emails from the list of recipients.
 */
data class BulkMailBatchConfigDto(

    /**
     * The size of the batching mail by recipients. <br>
     * If size = 0, one mail will be sent with all recipients.<br>
     * This option can be useful if the mail server cannot handle a large number of recipients in one email.
     */
    val size: Int = 0,

    /**
     * Sending a personalized mail to each recipient
     */
    val personalizedMails: Boolean = false

)
