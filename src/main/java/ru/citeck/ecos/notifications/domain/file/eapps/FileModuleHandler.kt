package ru.citeck.ecos.notifications.domain.file.eapps

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.module.controller.type.file.FileModule
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler
import ru.citeck.ecos.apps.module.handler.ModuleMeta
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.service.FileService
import java.util.function.Consumer

private const val MODULE_TYPE = "notification/file"

@Component
class FileModuleHandler(val fileService: FileService) : EcosModuleHandler<FileModule> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun deployModule(module: FileModule) {
        fileService.save(toDto(module))
    }

    private fun toDto(module: FileModule): FileWithMeta {
        val dto = FileWithMeta(
            id = module.path,
            data = module.data
        )

        log.debug { "Deploy new $MODULE_TYPE module: ${dto.id}" }

        return dto
    }

    override fun getModuleMeta(module: FileModule): ModuleWithMeta<FileModule> {
        val dto = toDto(module)
        return ModuleWithMeta(module, ModuleMeta(dto.id, emptyList()))
    }

    override fun getModuleType(): String {
        return MODULE_TYPE
    }

    override fun listenChanges(listener: Consumer<FileModule>) {
        //TODO: implement
    }

    override fun prepareToDeploy(module: FileModule): ModuleWithMeta<FileModule>? {
        return getModuleMeta(module)
    }
}
