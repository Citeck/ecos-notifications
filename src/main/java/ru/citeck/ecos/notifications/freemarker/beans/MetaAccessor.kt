package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps

private const val ID = "meta"

@Component
class MetaAccessor(private val ecosWebAppProps: EcosWebAppProps) : InjectedFreemarkerBean {

    companion object {
        private val customWebUrl = ThreadLocal.withInitial { "" }

        fun <T> doWithCustomWebUrl(url: String, action: () -> T): T {
            val prev = customWebUrl.get()
            customWebUrl.set(url)
            try {
                return action.invoke()
            } finally {
                customWebUrl.set(prev)
            }
        }
    }

    override fun getId(): String {
        return ID
    }

    fun getWebUrl(): String {
        var url = customWebUrl.get().ifBlank { ecosWebAppProps.webUrl }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }
}
