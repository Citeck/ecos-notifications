package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps

private const val ID = "link"

@Component
class LinkAccessor(private val ecosWebAppProps: EcosWebAppProps) : InjectedFreemarkerBean {

    override fun getId(): String {
        return ID
    }

    fun getRecordLink(recordRef: String): String {
        val webUrl = if (ecosWebAppProps.webUrl.endsWith("/")) {
            ecosWebAppProps.webUrl
        } else {
            ecosWebAppProps.webUrl + "/"
        }

        return "${webUrl}v2/dashboard?recordRef=$recordRef"
    }
}
