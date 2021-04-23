package ru.citeck.ecos.notifications.domain.template.eapps

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.artifact.controller.type.binary.BinArtifact
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.commons.utils.ZipUtils.extractZip
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.getLangKeyFromFileName
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import java.util.function.Consumer

private const val ARTIFACT_TYPE = "notification/template"

@Component
class NotificationTemplateArtifactHandler(
    val templateService: NotificationTemplateService
) : EcosArtifactHandler<BinArtifact> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun deployArtifact(artifact: BinArtifact) {
        templateService.update(toDto(artifact))
    }

    private fun toDto(module: BinArtifact): NotificationTemplateWithMeta {
        val meta = module.meta

        val dto = NotificationTemplateWithMeta(meta.get("id").asText())
        dto.notificationTitle = meta.get("notificationTitle", MLText::class.java)
        dto.name = meta.get("name").asText()
        val memDir = extractZip(module.data)
        dto.templateData = TemplateDataFinder(memDir).find()
        dto.model = meta.get("model").asMap(String::class.java, String::class.java)
        dto.multiTemplateConfig = meta.get("multiTemplateConfig").asList(MultiTemplateElementDto::class.java)

        log.debug("Deploy new $ARTIFACT_TYPE module: $dto")

        return dto
    }

    override fun getArtifactType(): String {
        return ARTIFACT_TYPE
    }

    override fun listenChanges(listener: Consumer<BinArtifact>) {

        templateService.addListener { dto ->

            val meta = ObjectData.create()
            meta.set("id", dto.id)
            meta.set("name", dto.name)
            meta.set("model", dto.model)
            meta.set("multiTemplateConfig", dto.multiTemplateConfig)
            meta.set("notificationTitle", dto.notificationTitle)

            val memDir = EcosMemDir()
            dto.templateData.values.forEach {
                memDir.createFile(it.name, it.data)
            }

            val archiveName = if (dto.templateData.size == 1) {
                dto.templateData.values.first().name + ".zip"
            } else {
                val firstFileName = dto.templateData.values.first().name
                firstFileName.substringBefore('.') + ".html.zip"
            }
            val path = archiveName.substringBefore('.') + "/" + archiveName
            listener.accept(BinArtifact(path, meta, ZipUtils.writeZipAsBytes(memDir)))
        }
    }

    inner class TemplateDataFinder(
        private val memDir: EcosMemDir
    ) {

        val templateData: MutableMap<String, TemplateDataDto> = mutableMapOf()

        fun find(): MutableMap<String, TemplateDataDto> {
            resolveFiles(memDir.getChildren())
            return templateData
        }

        private fun resolveFiles(files: List<EcosFile>) {
            files.forEach {
                if (it.isDirectory()) {
                    resolveFiles(it.getChildren())
                } else {
                    val langKey = getLangKeyFromFileName(it.getName())
                    val dataDto = TemplateDataDto(it.getName(), it.readAsBytes())
                    templateData[langKey] = dataDto

                    log.debug("Template data name: ${it.getName()} content: \n$${it.readAsString()}")
                }
            }
        }
    }
}
