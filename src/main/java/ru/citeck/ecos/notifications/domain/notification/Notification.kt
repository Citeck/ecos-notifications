package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import java.util.*

val DEFAULT_LOCALE: Locale = Locale.ENGLISH

class Notification (
        val type: NotificationType,
        val locale: Locale = DEFAULT_LOCALE,
        val recipients: List<String>,
        val template: NotificationTemplateWithMeta,
        val model: Map<String, Any>,
        val from: String
)
