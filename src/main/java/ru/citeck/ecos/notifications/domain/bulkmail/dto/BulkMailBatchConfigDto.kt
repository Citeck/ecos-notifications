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
     * This option can be useful if the mail server cannot handle a large number of recipients in one email.<br>
     * Note: the batch size is also the duplicate blast-radius on retry — if sending a batch
     * fails after the server accepted it partially, the whole batch is retried and every
     * recipient in it may receive the mail again. For important mailings prefer
     * [personalizedMails] = true (one recipient per notification, no shared blast-radius).
     */
    val size: Int = 0,

    /**
     * Sending a personalized mail to each recipient
     */
    val personalizedMails: Boolean = false

)
