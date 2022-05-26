package ru.citeck.ecos.notifications.domain.sender.command

import com.sun.istack.internal.ByteArrayDataSource
import ru.citeck.ecos.notifications.domain.notification.FitNotification
import ru.citeck.ecos.notifications.domain.notification.NotificationConstants
import ru.citeck.ecos.notifications.domain.notification.service.NotificationException
import java.util.*
import javax.activation.DataSource

data class CmdFitNotification(
    val body: String,
    var title: String? = "",
    val recipients: Set<String>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet(),
    var attachments: Map<String, AttachmentData> = emptyMap(),
    var data: Map<String, Any> = emptyMap()
) {
    constructor(fitNotification: FitNotification) : this(
        fitNotification.body,
        fitNotification.title,
        fitNotification.recipients,
        fitNotification.from,
        fitNotification.cc,
        fitNotification.bcc,
        emptyMap(),
        fitNotification.data
    ) {
        attachments =
            fitNotification.attachments.let {
                it.mapValues {
                    AttachmentData(
                        it.value.contentType,
                        it.value.inputStream.readBytes()
                    )
                }
            }
    }

    fun toFit(): FitNotification {
        val attachmentMap = mutableMapOf<String, DataSource>()
        attachments.forEach {
            attachmentMap[it.key] = ByteArrayDataSource(it.value.content, it.value.contentType)
        }
        return FitNotification(
            body,
            title,
            recipients,
            from,
            cc,
            bcc,
            attachmentMap,
            data
        )
    }
}
