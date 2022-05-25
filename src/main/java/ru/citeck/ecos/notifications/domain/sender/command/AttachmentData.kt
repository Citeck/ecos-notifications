package ru.citeck.ecos.notifications.domain.sender.command

data class AttachmentData(
    val contentType: String,
    val content: ByteArray) {
}
