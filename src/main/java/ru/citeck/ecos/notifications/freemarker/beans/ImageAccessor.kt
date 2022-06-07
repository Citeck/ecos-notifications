package ru.citeck.ecos.notifications.freemarker.beans

import org.apache.commons.io.FilenameUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.service.FileService
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean
import javax.xml.bind.DatatypeConverter

private const val ID = "image"
private const val IMAGE_DATA_FORMAT = "data:image/%s;base64,%s"

@Component
class ImageAccessor(val fileService: FileService) : InjectedFreemarkerBean {

    override fun getId(): String {
        return ID
    }

    fun toBase64Data(fileName: String): String {
        val extension = FilenameUtils.getExtension(fileName)
        val base64 = toBase64(fileName)
        return String.format(IMAGE_DATA_FORMAT, extension, base64)
    }

    fun toBase64(fileName: String): String {
        val file = getFile(fileName)
        return DatatypeConverter.printBase64Binary(file.data)
    }

    private fun getFile(fileName: String): FileWithMeta {
        return fileService.findById(fileName).orElseThrow {
            IllegalArgumentException("File with id: <$fileName> not found")
        }
    }
}
