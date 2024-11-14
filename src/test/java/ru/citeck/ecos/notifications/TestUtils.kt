package ru.citeck.ecos.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.core.io.ClassPathResource
import org.springframework.util.StreamUtils
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import java.nio.charset.Charset
import javax.mail.internet.MimeMultipart

const val RECIPIENT_EMAIL = "some-recipient@gmail.com"
const val TEXT_TXT_FILENAME = "test.txt"
const val TEXT_TXT_EXT = "txt"

fun loadAllTemplates(location: String): List<NotificationTemplateDto> {
    val root = ResourceUtils.getFile("${ResourceUtils.CLASSPATH_URL_PREFIX}$location")
    val result = ArrayList<NotificationTemplateDto>()
    EcosStdFile(root).findFiles("**.json").forEach {
        val value = Json.mapper.read(it, NotificationTemplateDto::class.java)!!
        result.add(value)
    }
    return result
}

fun stringFromResource(path: String): String {
    val createTypeResource = ClassPathResource(path)
    return StreamUtils.copyToString(createTypeResource.inputStream, Charset.defaultCharset())
}

fun hasAttachment(
    content: MimeMultipart,
    mimeType: String,
    attachmentName: String,
    charset: String?,
    valueToCompare: String?
): Boolean {
    val contentHeader = "$mimeType;${charset?.let{" charset=$charset;"} ?: ""} name=$attachmentName"
    for (i in 0 until content.count) {
        if (content.getBodyPart(i).getHeader("Content-Type")
            .any { it == contentHeader }
        ) {
            assertNotNull(content.getBodyPart(i).content)
            valueToCompare?.let {
                assertEquals(valueToCompare, content.getBodyPart(i).content)
            }
            return true
        }
    }
    return false
}
