package ru.citeck.ecos.notifications.domain.template

import org.apache.commons.lang.StringUtils
import ru.citeck.ecos.commons.data.ObjectData
import java.util.*
import java.util.regex.Pattern

private val TEMPLATE_LANG_KEY_PATTERN: Pattern = Pattern.compile(".*_(\\w+).*")

private const val URL_PARAM = "url"
private const val BASE_64_DELIMITER = ","

fun getLangKeyFromFileName(fileName: String): String {
    val matcher = TEMPLATE_LANG_KEY_PATTERN.matcher(fileName)
    return if (matcher.find()) {
        matcher.group(1)
    } else Locale.ENGLISH.toString()
}

fun getContentBytesFromBase64ObjectData(objectData: ObjectData): ByteArray {
    var base64Content = objectData.get(URL_PARAM, "")
    base64Content = StringUtils.substringAfter(base64Content, BASE_64_DELIMITER)
    return Base64.getDecoder().decode(base64Content.toByteArray(Charsets.UTF_8))
}
