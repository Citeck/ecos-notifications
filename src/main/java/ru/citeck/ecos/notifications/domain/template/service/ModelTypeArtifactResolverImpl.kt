package ru.citeck.ecos.notifications.domain.template.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.ecostype.service.ModelTypeArtifactResolver
import ru.citeck.ecos.records2.RecordRef
import java.util.*

@Component
class ModelTypeArtifactResolverImpl(
    private val notificationTemplateService: NotificationTemplateService
) : ModelTypeArtifactResolver {

    override fun getTypeArtifacts(typeRef: RecordRef): List<RecordRef> {
        return notificationTemplateService.findTemplateRefsForTypes(Collections.singletonList(typeRef))
    }
}
