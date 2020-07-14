package ru.citeck.ecos.notifications.domain.template.handler

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
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern

@Component
class NotificationTemplateModuleHandler : EcosModuleHandler<BinModule> {

    companion object {
        private val LANG_KEY_PATTERN = Pattern.compile(".*_(\\w+).*")
        private const val DEFAULT_LANG_KEY = "en"
        private val log = KotlinLogging.logger {}
    }

    @Autowired
    private lateinit var templateService: NotificationTemplateService

    override fun deployModule(module: BinModule) {
        templateService.update(toDto(module))
    }

    private fun toDto(module: BinModule): NotificationTemplateDto {
        val meta = module.meta

        val dto = NotificationTemplateDto(meta.get("id").asText())
        dto.notificationTitle = meta.get("notificationTitle", MLText::class.java)
        dto.name = meta.get("name").asText()
        val memDir = extractZip(module.data)
        dto.data = getTemplateDataFromMemDir(memDir)

        log.debug("Deploy new template module: $dto")
        if (log.isDebugEnabled) {
            memDir.getChildren().forEach {
                val name = it.getName()
                val content = it.readAsString()
                log.debug("name: $name content: \n$content")
            }
        }

        return dto
    }

    private fun getTemplateDataFromMemDir(memDir: EcosMemDir): Map<String, TemplateDataDto> {
        val templateData: MutableMap<String, TemplateDataDto> = HashMap()
        memDir.getChildren().forEach(Consumer { file: EcosFile ->
            val langKey = getLangKeyFromFileName(file.getName())
            val dataDto = TemplateDataDto(file.getName(), file.readAsBytes())
            templateData[langKey] = dataDto
        })
        return templateData
    }

    private fun getLangKeyFromFileName(fileName: String): String {
        val matcher = LANG_KEY_PATTERN.matcher(fileName)
        return if (matcher.find()) {
            matcher.group(1)
        } else DEFAULT_LANG_KEY
    }

    override fun getModuleMeta(module: BinModule): ModuleWithMeta<BinModule> {
        val dto = toDto(module)
        return ModuleWithMeta(module, ModuleMeta(dto.id, emptyList()))
    }

    override fun getModuleType(): String {
        return "notification/template"
    }

    override fun listenChanges(listener: Consumer<BinModule>) {}

    override fun prepareToDeploy(module: BinModule): ModuleWithMeta<BinModule>? {
        return getModuleMeta(module)
    }
}
