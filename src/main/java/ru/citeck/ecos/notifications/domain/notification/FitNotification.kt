package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.records2.RecordRef
import javax.activation.DataSource

data class FitNotification(
    val body: String,
    var title: String? = "",
    val recipients: Set<String>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet(),
    var attachments: Map<String, DataSource> = emptyMap(),
    var data: Map<String, Any> = emptyMap(),
    var templateRef: RecordRef? = null
) {
    override fun toString(): String {
        val attachmentStr = attachments
            .map { it.key }.joinToString(",", "{", "}")
        return "FitNotification(body='$body', title=$title, recipients=$recipients, from='$from', " +
            "cc=$cc, bcc=$bcc, attachments=$attachmentStr,  data=$data, templateRef=$templateRef)"
    }
}
