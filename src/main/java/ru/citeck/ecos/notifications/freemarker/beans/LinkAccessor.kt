package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean
import ru.citeck.ecos.notifications.freemarker.TemplateProcCtxKey

private const val ID = "link"

@Component
class LinkAccessor(
    private val metaAccessor: MetaAccessor,
    private val ecosContext: EcosContext
) : InjectedFreemarkerBean {

    override fun getId(): String {
        return ID
    }

    fun getRecordLink(recordRef: String): String {
        val workspace = ecosContext.get(TemplateProcCtxKey.WORKSPACE) as? String ?: ""
        var result = "${metaAccessor.getWebUrl()}v2/dashboard?recordRef=$recordRef"
        if (workspace.isNotBlank()) {
            result += "&ws=$workspace"
        }
        return result
    }
}
