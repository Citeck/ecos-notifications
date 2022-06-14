package ru.citeck.ecos.notifications.domain.sender.command

data class CommandSenderConfig(
    var targetApp: String,
    var commandType: String
)
