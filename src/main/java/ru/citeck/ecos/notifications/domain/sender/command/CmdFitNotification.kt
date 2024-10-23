package ru.citeck.ecos.notifications.domain.sender.command

import jakarta.activation.DataSource
import jakarta.mail.util.ByteArrayDataSource
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.webapp.api.entity.EntityRef

class CmdFitNotification(
    val body: String,
    var title: String? = "",
    val recipients: Set<String>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet(),
    var webUrl: String = "",
    var attachments: Map<String, AttachmentData> = emptyMap(),
    var data: Map<String, Any?> = emptyMap(),
    var templateRef: EntityRef? = null
) {
    constructor(fitNotification: FitNotification) : this(
        fitNotification.body,
        fitNotification.title,
        fitNotification.recipients,
        fitNotification.from,
        fitNotification.cc,
        fitNotification.bcc,
        fitNotification.webUrl,
        emptyMap(),
        fitNotification.data,
        fitNotification.templateRef
    ) {
        attachments = fitNotification.attachments.let { attachments ->
            attachments.mapValues {
                AttachmentData(
                    it.value.contentType,
                    it.value.inputStream.readBytes()
                )
            }
        }
    }

    companion object {

        fun convertAttachments(attachments: Map<String, AttachmentData>): Map<String, DataSource> {
            val attachmentMap = mutableMapOf<String, DataSource>()
            attachments.forEach {
                attachmentMap[it.key] = ByteArrayDataSource(it.value.content, it.value.contentType)
            }
            return attachmentMap
        }
    }
}
