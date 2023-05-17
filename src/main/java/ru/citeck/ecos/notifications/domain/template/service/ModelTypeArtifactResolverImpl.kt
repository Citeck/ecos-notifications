package ru.citeck.ecos.notifications.domain.template.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.ecostype.service.ModelTypeArtifactResolver
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

@Component
class ModelTypeArtifactResolverImpl(
    private val notificationTemplateService: NotificationTemplateService
) : ModelTypeArtifactResolver {

    override fun getTypeArtifacts(typeRef: EntityRef): List<EntityRef> {
        return notificationTemplateService.findTemplateRefsForTypes(Collections.singletonList(typeRef))
    }
}
