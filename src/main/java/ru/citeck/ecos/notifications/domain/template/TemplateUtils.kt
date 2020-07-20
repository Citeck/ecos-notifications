package ru.citeck.ecos.notifications.domain.template

import java.util.*
import java.util.regex.Pattern

private val TEMPLATE_LANG_KEY_PATTERN: Pattern = Pattern.compile(".*_(\\w+).*")

fun getLangKeyFromFileName(fileName: String): String {
    val matcher = TEMPLATE_LANG_KEY_PATTERN.matcher(fileName)
    return if (matcher.find()) {
        matcher.group(1)
    } else Locale.ENGLISH.toString()
}
