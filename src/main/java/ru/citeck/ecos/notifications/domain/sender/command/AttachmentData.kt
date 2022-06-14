package ru.citeck.ecos.notifications.domain.sender.command

data class AttachmentData(
    val contentType: String,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachmentData) return false

        if (contentType != other.contentType) return false
        if (!content.contentEquals(other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}
