package ru.citeck.ecos.notifications.domain.event.dto

data class ErrorInfo(
    var message: String = "",
    var stackTrace: String = ""
)
