package ru.citeck.ecos.notifications.domain.bulkmail

/**
 * @author Roman Makarskiy
 */
enum class BulkMailStatus(val status: String) {
    NEW("new"),
    WAIT_FOR_DISPATCH("wait-for-dispatch"),
    TRYING_TO_DISPATCH("trying-to-dispatch"),
    SENT("sent"),
    ERROR("error");

    companion object {
        fun from(status: String): BulkMailStatus = values().find { it.status == status }
            ?: throw IllegalArgumentException("Bulk mail state for status: $status not found")
    }
}
