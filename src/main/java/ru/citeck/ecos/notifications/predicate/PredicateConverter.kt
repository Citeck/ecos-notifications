package ru.citeck.ecos.notifications.predicate

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.apache.commons.lang3.StringUtils
import org.springframework.data.jpa.domain.Specification
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import java.time.Instant

fun <T> Predicate.toDefaultEntitySpec(): Specification<T>? {
    toValueModifiedSpec<T>()?.let { return it }

    val predicateDto = PredicateUtils.convertToDto(this, PredicateDto::class.java)
    var spec: Specification<T>? = null

    if (StringUtils.isNotBlank(predicateDto.name)) {
        spec =
            Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                builder.like(
                    builder.lower(root.get("name")),
                    "%" + predicateDto.name.lowercase() + "%"
                )
            }
    }

    if (StringUtils.isNotBlank(predicateDto.moduleId)) {
        val idSpec =
            Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                builder.like(
                    builder.lower(root.get("extId")),
                    "%" + predicateDto.moduleId.lowercase() + "%"
                )
            }
        spec = spec?.or(idSpec) ?: idSpec
    }

    return spec
}

fun <T> Predicate.toValueModifiedSpec(): Specification<T>? {
    if (this is ValuePredicate) {
        if (RecordConstants.ATT_MODIFIED == getAttribute() && ValuePredicate.Type.GT == getType()) {
            val instant = Json.mapper.convert(getValue(), Instant::class.java)
            if (instant != null) {
                return Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.greaterThan(
                        root.get<Any>("lastModifiedDate").`as`(
                            Instant::class.java
                        ),
                        instant
                    )
                }
            }
        }
    }

    return null
}
