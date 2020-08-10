package ru.citeck.ecos.notifications

import org.springframework.core.io.ClassPathResource
import org.springframework.util.StreamUtils
import java.nio.charset.Charset

fun stringJsonFromResource(path: String): String {
    val createTypeResource = ClassPathResource(path)
    return StreamUtils.copyToString(createTypeResource.inputStream, Charset.defaultCharset())
}
