package ru.citeck.ecos.notifications.domain.sender.command

import ru.citeck.ecos.notifications.domain.notification.FitNotification

data class CmdFitNotification(
    val body: String,
    var title: String? = "",
    val recipients: Set<String>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet(),
    var attachments: Map<String, AttachmentData> = emptyMap(),
    var data: Map<String, Any> = emptyMap()
){
    constructor(fitNotification: FitNotification) : this (
        fitNotification.body,
        fitNotification.title,
        fitNotification.recipients,
        fitNotification.from,
        fitNotification.cc,
        fitNotification.bcc,
        emptyMap(),
        fitNotification.data
        ){
        attachments =
            fitNotification.attachments.let {
                it.mapValues { AttachmentData(it.value.contentType,
                        it.value.inputStream.readBytes())
                }
            }
    }
}
