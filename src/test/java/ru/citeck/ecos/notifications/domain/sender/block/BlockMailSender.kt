package ru.citeck.ecos.notifications.domain.sender.block

import org.springframework.stereotype.Component
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus
import ru.citeck.ecos.notifications.lib.NotificationSenderSendStatus.*

var blockedMailSenderForceState: String? = null

@Component
class BlockMailSender : CommandExecutor<BlockMailDto> {

    override fun execute(dto: BlockMailDto): NotificationSenderSendStatus {
        return when (val state = blockedMailSenderForceState ?: dto.title) {
            BLOCKED.toString() -> BLOCKED
            SKIPPED.toString() -> SKIPPED
            else -> error("Not supported: $state")
        }
    }
}

@CommandType("block-email-notification")
data class BlockMailDto(
    var title: String
)
