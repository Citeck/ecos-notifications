package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps

private const val ID = "meta"

@Component
class MetaAccessor(private val ecosWebAppProps: EcosWebAppProps) : InjectedFreemarkerBean {

    override fun getId(): String {
        return ID
    }

    fun getWebUrl(): String {
        return ecosWebAppProps.webUrl
    }
}
