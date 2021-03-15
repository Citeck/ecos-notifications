package ru.citeck.ecos.notifications.domain.template

import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.apps.artifact.controller.type.binary.BinArtifact
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.eapps.NotificationTemplateArtifactHandler
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import java.util.function.Consumer

class NotificationTemplateArtifactHandlerTest {

    @Test
    fun test() {

        val updatedDto = mutableListOf<NotificationTemplateWithMeta>()
        val onDtoChanged = mutableListOf<Consumer<NotificationTemplateWithMeta>>()

        val service = Mockito.mock(NotificationTemplateService::class.java)
        Mockito.`when`(service.update(Mockito.any())).then { args ->
            updatedDto.add(args.getArgument(0))
        }
        Mockito.`when`(service.addListener(Mockito.any())).then { args ->
            onDtoChanged.add(args.getArgument(0))
        }

        val metaFile = ResourceUtils.getFile("classpath:template/NotificationTemplateArtifactHandlerTest/meta.json")
        val meta = Json.mapper.read(EcosStdFile(metaFile), ObjectData::class.java)!!
        val dataDir = ResourceUtils.getFile("classpath:template/NotificationTemplateArtifactHandlerTest/data")
        val data = ZipUtils.writeZipAsBytes(EcosStdFile(dataDir))

        val handler = NotificationTemplateArtifactHandler(service)

        handler.deployArtifact(BinArtifact("NotificationTemplateArtifactHandlerTest", meta, data))
        assertEquals(1, updatedDto.size)

        val binArtifacts = mutableListOf<BinArtifact>()
        handler.listenChanges(Consumer {
            binArtifacts.add(it)
        })
        onDtoChanged[0].accept(updatedDto[0])

        handler.deployArtifact(binArtifacts[0])
        assertEquals(updatedDto[0], updatedDto[1])
    }
}
