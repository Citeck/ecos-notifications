package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean

private const val ID = "link"

@Component
class LinkAccessor(private val metaAccessor: MetaAccessor) : InjectedFreemarkerBean {

    override fun getId(): String {
        return ID
    }

    fun getRecordLink(recordRef: String): String {
        return "${metaAccessor.getWebUrl()}v2/dashboard?recordRef=$recordRef"
    }
}
