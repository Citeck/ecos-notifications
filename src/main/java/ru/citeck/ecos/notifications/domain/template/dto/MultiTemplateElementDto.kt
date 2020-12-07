package ru.citeck.ecos.notifications.domain.template.dto

import ecos.com.fasterxml.jackson210.annotation.JsonInclude
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicate

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
data class MultiTemplateElementDto (
    var template: RecordRef? = null,
    var type: RecordRef? = null,
    var condition: Predicate? = null
)
