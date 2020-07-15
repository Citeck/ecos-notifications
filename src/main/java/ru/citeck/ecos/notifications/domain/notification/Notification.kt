package ru.citeck.ecos.notifications.domain.notification

import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import java.util.*

class Notification (
    val type: NotificationType,
    val locale: Locale = Locale.ENGLISH,
    val recipients: List<String>,
    val template: NotificationTemplateDto,
    val model: Map<String, Any>,
    val from: String
)
