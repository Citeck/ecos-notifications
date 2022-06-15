package ru.citeck.ecos.notifications.domain.sender.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.service.NotificationsSenderService
import java.util.function.Consumer

@Component
class NotificationsSenderArtifactHandler(var service: NotificationsSenderService) :
    EcosArtifactHandler<NotificationsSenderDto> {

    override fun deleteArtifact(artifactId: String) {
        service.delete(artifactId)
    }

    override fun deployArtifact(artifact: NotificationsSenderDto) {
        service.save(artifact)
    }

    override fun getArtifactType(): String {
        return "notification/sender"
    }

    override fun listenChanges(listener: Consumer<NotificationsSenderDto>) {
        service.onSenderChanged { _, after -> after?.let { listener.accept(it) } }
    }
}
