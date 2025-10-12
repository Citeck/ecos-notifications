package ru.citeck.ecos.notifications.freemarker

import freemarker.cache.TemplateLoader
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.notifications.domain.notification.DEFAULT_LOCALE
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.Reader
import java.time.Instant

const val LANG_SPECIFY_SEPARATOR = "_"

@Component
class EcosTemplateLoader(
    @Qualifier("domainNotificationTemplateService")
    private val templateService: NotificationTemplateService,
    private val workspaceService: WorkspaceService
) : TemplateLoader {

    override fun closeTemplateSource(templateSource: Any?) {
    }

    override fun getReader(templateSource: Any?, encoding: String?): Reader {
        if (templateSource is Template) {
            return InputStreamReader(ByteArrayInputStream(templateSource.data))
        }

        throw IllegalArgumentException("templateSource wasn't a Template")
    }

    override fun getLastModified(templateSource: Any?): Long {
        if (templateSource is Template) {
            return templateSource.lastModified.toEpochMilli()
        }

        return -1
    }

    override fun findTemplateSource(name: String?): Any? {
        if (StringUtils.isBlank(name)) {
            return null
        }

        val langKey = StringUtils.substringAfterLast(name, LANG_SPECIFY_SEPARATOR)
        var cleanedName = name
        if (StringUtils.isNoneBlank(langKey)) {
            cleanedName = StringUtils.substringBeforeLast(name, "$LANG_SPECIFY_SEPARATOR$langKey")
        }

        val ref = EntityRef.valueOf(cleanedName)
        val id = ref.getLocalId()

        val found = templateService.findById(workspaceService.convertToIdInWs(id))
        if (!found.isPresent) {
            return null
        }

        val foundTemplate = found.get()

        val templateData = resolveTemplateData(foundTemplate.templateData, langKey)
        val modified = foundTemplate.modified ?: Instant.MIN

        if (modified == null || templateData == null) {
            return null
        }

        return Template(name!!, modified, templateData)
    }

    fun resolveTemplateData(templateData: Map<String, TemplateDataDto>, lang: String): ByteArray? {
        if (templateData.isEmpty()) {
            return null
        }

        return templateData[lang]?.data
            ?: templateData[DEFAULT_LOCALE.toString()]?.data
            ?: templateData.entries.first().value.data
    }

    data class Template(
        val id: String,
        val lastModified: Instant,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Template

            if (id != other.id) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
