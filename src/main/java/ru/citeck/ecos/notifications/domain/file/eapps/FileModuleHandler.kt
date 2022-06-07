package ru.citeck.ecos.notifications.domain.file.eapps

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.artifact.controller.type.file.FileArtifact
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.service.FileService
import java.util.function.Consumer

private const val ARTIFACT_TYPE = "notification/file"

@Component
class FileModuleHandler(val fileService: FileService) : EcosArtifactHandler<FileArtifact> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun deployArtifact(artifact: FileArtifact) {
        fileService.save(toDto(artifact))
    }

    private fun toDto(module: FileArtifact): FileWithMeta {
        val dto = FileWithMeta(
            id = module.path,
            data = module.data
        )

        log.debug { "Deploy new $ARTIFACT_TYPE module: ${dto.id}" }

        return dto
    }

    override fun getArtifactType(): String {
        return ARTIFACT_TYPE
    }

    override fun listenChanges(listener: Consumer<FileArtifact>) {
        // TODO: implement
    }

    override fun deleteArtifact(artifactId: String) {
        fileService.deleteById(artifactId)
    }
}
