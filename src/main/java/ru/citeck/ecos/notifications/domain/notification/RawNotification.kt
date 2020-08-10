package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import java.util.*

val DEFAULT_LOCALE: Locale = Locale.ENGLISH

data class RawNotification(
    val type: NotificationType,
    val locale: Locale = DEFAULT_LOCALE,
    val recipients: Set<String>,
    val template: NotificationTemplateWithMeta,
    val model: Map<String, Any>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet()
)