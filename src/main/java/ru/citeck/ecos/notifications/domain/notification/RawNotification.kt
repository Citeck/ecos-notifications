package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

val DEFAULT_LOCALE: Locale = Locale.ENGLISH

data class RawNotification(
    var record: EntityRef,
    val type: NotificationType,
    val locale: Locale = DEFAULT_LOCALE,
    val webUrl: String = "",
    val recipients: Set<String>,
    val title: String = "",
    val body: String = "",
    val template: NotificationTemplateWithMeta? = null,
    val model: Map<String, Any?>,
    val from: String,
    var cc: Set<String> = emptySet(),
    var bcc: Set<String> = emptySet()
)
