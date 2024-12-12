package ru.citeck.ecos.notifications.service.senders

enum class NotificationSenderType(val type: String) {
    DEFAULT("default"),
    COMMAND("command"),
    UNKNOWN("unknown");

    companion object {
        fun fromType(type: String?): NotificationSenderType {
            return NotificationSenderType.values().firstOrNull { it.type == type } ?: UNKNOWN
        }
    }
}
