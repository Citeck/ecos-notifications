package ru.citeck.ecos.notifications.domain.notification

import javax.activation.DataSource

data class FitNotification(
    val body: String,
    var title: String? = "",
    val recipients: Set<String>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet(),
    var attachments: Map<String, DataSource> = emptyMap(),
    var data: Map<String, Any> = emptyMap()
)
