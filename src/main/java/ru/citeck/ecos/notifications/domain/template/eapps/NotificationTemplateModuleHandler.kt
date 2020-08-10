package ru.citeck.ecos.notifications.domain.template.eapps

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.module.controller.type.binary.BinModule
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler
import ru.citeck.ecos.apps.module.handler.ModuleMeta
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.utils.ZipUtils.extractZip
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.getLangKeyFromFileName
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import java.util.function.Consumer

@Component
class NotificationTemplateModuleHandler : EcosModuleHandler<BinModule> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Autowired
    private lateinit var templateService: NotificationTemplateService

    override fun deployModule(module: BinModule) {
        templateService.update(toDto(module))
    }

    private fun toDto(module: BinModule): NotificationTemplateWithMeta {
        val meta = module.meta

        val dto = NotificationTemplateWithMeta(meta.get("id").asText())
        dto.notificationTitle = meta.get("notificationTitle", MLText::class.java)
        dto.name = meta.get("name").asText()
        val memDir = extractZip(module.data)
        dto.templateData = TemplateDataFinder(memDir).find()
        dto.model = meta.get("model").asMap(String::class.java, String::class.java)
        dto.multiTemplateConfig = meta.get("multiTemplateConfig").asList(MultiTemplateElementDto::class.java)

        log.debug("Deploy new template module: $dto")

        return dto
    }

    override fun getModuleMeta(module: BinModule): ModuleWithMeta<BinModule> {
        val dto = toDto(module)
        return ModuleWithMeta(module, ModuleMeta(dto.id, emptyList()))
    }

    override fun getModuleType(): String {
        return "notification/template"
    }

    override fun listenChanges(listener: Consumer<BinModule>) {
        //TODO: implement
    }

    override fun prepareToDeploy(module: BinModule): ModuleWithMeta<BinModule>? {
        return getModuleMeta(module)
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
