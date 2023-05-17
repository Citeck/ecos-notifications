package ru.citeck.ecos.notifications.domain.template.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.webapp.api.entity.EntityRef

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
data class MultiTemplateElementDto(
    var template: EntityRef? = null,
    var type: EntityRef? = null,
    var condition: Predicate? = null
)
