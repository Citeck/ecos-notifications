package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean
import ru.citeck.ecos.notifications.freemarker.TemplateProcCtxKey
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps

private const val ID = "meta"

@Component
class MetaAccessor(
    private val ecosWebAppProps: EcosWebAppProps,
    private val ecosContext: EcosContext
) : InjectedFreemarkerBean {

    override fun getId(): String {
        return ID
    }

    fun getWebUrl(): String {
        val customWebUrl = ecosContext.get(TemplateProcCtxKey.CUSTOM_WEB_URL) as? String ?: ""

        var url = customWebUrl.ifBlank { ecosWebAppProps.webUrl }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }
}
