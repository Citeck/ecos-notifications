package ru.citeck.ecos.notifications.domain

import mu.KotlinLogging
import org.apache.commons.lang.StringUtils
import org.springframework.data.jpa.domain.Specification
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.AndPredicate
import ru.citeck.ecos.records2.predicate.model.ComposedPredicate
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import java.lang.reflect.Field
import java.time.DateTimeException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root
import java.util.function.Consumer

class SpecificationUtils {
    companion object {
        private val log = KotlinLogging.logger {}

        fun canSearchAsString(searchAtClass: Class<*>, attributeName: String, attributeValue: String): Boolean {
            var searchField = getField(searchAtClass, attributeName)
            if (searchField == null) {
                return false
            }
            if (searchField.type == Instant::class.java || searchField.type == java.util.Date::class.java
                || searchField.type.isEnum
            ) {
                return false
            }
            if (searchField.type == Integer::class.java || searchField.type == java.lang.Integer::class.java
                || searchField.type == java.lang.Long::class.java || searchField.type == Long::class.java
            ) {
                val objectValue = getObjectValue(searchAtClass, attributeName, attributeValue)
                return objectValue != null
            }
            return true
        }

        fun getField(searchAtClass: Class<*>, attributeName: String): Field? {
            var searchField: Field? = null
            try {
                searchField = searchAtClass.getDeclaredField(attributeName)
            } catch (e: NoSuchFieldException) {
                var superclass: Class<in Nothing> = searchAtClass.superclass
                while (searchField == null) {
                    searchField = superclass.getDeclaredField(attributeName)
                    superclass = superclass.superclass
                }
            }
            return searchField
        }

        private fun getObjectValue(
            searchAtClass: Class<*>,
            attributeName: String, attributeValue: String
        ): Comparable<*>? {
            var searchField = getField(searchAtClass, attributeName)
            if (searchField != null)
                try {
                    when (searchField.type) {
                        java.lang.String::class.java, String::class.java -> {
                            return attributeValue
                        }

                        java.lang.Long::class.java, Long::class.java -> {
                            return attributeValue.toLong()
                        }

                        java.lang.Integer::class.java, Integer::class.java -> {
                            return attributeValue.toInt()
                        }

                        java.lang.Boolean::class.java, Boolean::class.java -> {
                            return attributeValue.toBoolean()
                        }

                        java.util.Date::class.java -> {
                            //saved values has no milliseconds part cause of dateFormat
                            try {
                                val calendar = Calendar.getInstance()
                                calendar.timeInMillis = attributeValue.toLong()
                                calendar[Calendar.MILLISECOND] = 0
                                return calendar.time
                            } catch (formatException: NumberFormatException) {
                                try {
                                    var valueObject =
                                        Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(attributeValue))
                                    valueObject = valueObject.truncatedTo(ChronoUnit.SECONDS)
                                    return Date.from(valueObject)
                                } catch (e: DateTimeException) {
                                    log.error(
                                        "Failed to convert attribute '{}' value ({}) to date", attributeName,
                                        attributeValue, e
                                    )
                                }
                            }
                        }

                        Instant::class.java -> {
                            return Json.mapper.convert(attributeValue.toLong(), Instant::class.java)
                        }

                        java.lang.Float::class.java, Float::class.java -> {
                            return attributeValue.toFloat()
                        }

                        else -> {
                            if (searchField.type.isEnum) {
                                val result = searchField.type.enumConstants
                                    .firstOrNull { attributeValue.equals((it as Enum<*>).name) }
                                return result as Comparable<*>?
                            }
                            log.error("Unexpected attribute type {} for predicate", searchField.type)
                        }
                    }
                } catch (e: NumberFormatException) {
                    log.error(
                        "Failed to convert attribute '{}' value ({}) to number", attributeName,
                        attributeValue, e
                    )
                }
            return null
        }

        private fun isAttributeNameNotValidDefault(attributeName: String): Boolean {
            return RecordConstants.ATT_TYPE.equals(attributeName)
        }

        fun replaceAttributeNameByValidDefault(attributeName: String): String {
            if (RecordConstants.ATT_MODIFIED.equals(attributeName)) {
                return "lastModifiedDate"
            }
            if (RecordConstants.ATT_MODIFIER.equals(attributeName)) {
                return "lastModifiedBy"
            }
            if ("moduleId".equals(attributeName)) {
                return "extId"
            }
            return attributeName
        }

        fun <T> fromValuePredicate(searchAtEntityClass: Class<*>, valuePredicate: ValuePredicate):
            Specification<T>? {
            //ValuePredicate.Type.IN was not implemented
            if (StringUtils.isBlank(valuePredicate.getAttribute())) {
                return null
            }
            var attributeName = replaceAttributeNameByValidDefault(StringUtils.trim(valuePredicate.getAttribute()))
            if (searchAtEntityClass is SearchAtEntity) {
                attributeName = (searchAtEntityClass as SearchAtEntity).replaceAttributeNameByValid(attributeName)
                if ((searchAtEntityClass as SearchAtEntity).isAttributeNameNotValid(attributeName)) {
                    return null
                }
            } else {
                if (isAttributeNameNotValidDefault(attributeName)) {
                    return null
                }
            }

            var specification: Specification<T>? = null
            if (ValuePredicate.Type.CONTAINS == valuePredicate.getType()
                || ValuePredicate.Type.LIKE == valuePredicate.getType()
            ) {
                if (canSearchAsString(searchAtEntityClass, attributeName, valuePredicate.getValue().asText())) {
                    val attributeValue = "%" + valuePredicate.getValue().asText().lowercase() + "%"
                    specification = Specification { root: Root<T>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.like(builder.lower(root.get(attributeName)), attributeValue)
                    }
                } else {
                    val attributeField = getField(searchAtEntityClass, attributeName)
                    if (ValuePredicate.Type.CONTAINS == valuePredicate.getType() && attributeField != null
                        && attributeField.type.isEnum
                    ) {
                        val attributeValue =
                            getObjectValue(searchAtEntityClass, attributeName, valuePredicate.getValue().asText())
                        log.debug { "Enum value: $attributeValue" }
                        if (attributeValue != null) {
                            specification = Specification { root: Root<T>,
                                                            _: CriteriaQuery<*>?,
                                                            builder: CriteriaBuilder ->
                                builder.equal(root.get<String>(attributeName), attributeValue)
                            }
                        }
                    } else {
                        return null
                    }
                }
            } else {
                val objectValue: Comparable<*>? = getObjectValue(
                    searchAtEntityClass, attributeName, valuePredicate.getValue().asText()
                )
                if (objectValue != null) {
                    if (ValuePredicate.Type.EQ == valuePredicate.getType()) {
                        specification = Specification { root: Root<T>,
                                                        _: CriteriaQuery<*>?,
                                                        builder: CriteriaBuilder ->
                            builder.equal(root.get<Any>(attributeName), objectValue)
                        }
                    } else if (ValuePredicate.Type.GT == valuePredicate.getType()) {
                        specification = Specification { root: Root<T>,
                                                        _: CriteriaQuery<*>?,
                                                        builder: CriteriaBuilder ->
                            builder.greaterThan<Comparable<*>>(
                                root.get<Comparable<Comparable<*>>>(attributeName),
                                objectValue
                            )
                        }
                    } else if (ValuePredicate.Type.GE == valuePredicate.getType()) {
                        specification = Specification { root: Root<T>,
                                                        _: CriteriaQuery<*>?,
                                                        builder: CriteriaBuilder ->
                            builder.greaterThanOrEqualTo<Comparable<*>>(
                                root.get<Comparable<Comparable<*>>>(attributeName),
                                objectValue
                            )
                        }
                    } else if (ValuePredicate.Type.LT == valuePredicate.getType()) {
                        specification = Specification { root: Root<T>,
                                                        _: CriteriaQuery<*>?,
                                                        builder: CriteriaBuilder ->
                            builder.lessThan<Comparable<*>>(
                                root.get<Comparable<Comparable<*>>>(attributeName),
                                objectValue
                            )
                        }
                    } else if (ValuePredicate.Type.LE == valuePredicate.getType()) {
                        specification = Specification { root: Root<T>,
                                                        _: CriteriaQuery<*>?,
                                                        builder: CriteriaBuilder ->
                            builder.lessThanOrEqualTo<Comparable<*>>(
                                root.get<Comparable<Comparable<*>>>(attributeName),
                                objectValue
                            )
                        }
                    }
                }
            }
            return specification
        }

        fun <T> getSpecificationFromPredicate(searchAtEntityClass: Class<*>, predicate: Predicate?):
            Specification<T>? {
            if (predicate == null) {
                return null
            }
            var result: Specification<T>? = null
            if (predicate is ComposedPredicate) {
                val specifications: ArrayList<Specification<T>> =
                    ArrayList<Specification<T>>()
                predicate.getPredicates().forEach(
                    Consumer { subPredicate: Predicate? ->
                        var subSpecification: Specification<T>? = null
                        if (subPredicate is ValuePredicate) {
                            subSpecification = fromValuePredicate(searchAtEntityClass, subPredicate)
                        } else if (subPredicate is ComposedPredicate) {
                            subSpecification = getSpecificationFromPredicate(searchAtEntityClass, subPredicate)
                        }
                        if (subSpecification != null) {
                            specifications.add(subSpecification)
                        }
                    })
                if (specifications.isNotEmpty()) {
                    result = specifications[0]
                    if (specifications.size > 1) {
                        for (idx in 1 until specifications.size) {
                            result =
                                if (predicate is AndPredicate) {
                                    result!!.and(specifications[idx])
                                } else {
                                    result!!.or(specifications[idx])
                                }
                        }
                    }
                }
                return result
            } else if (predicate is ValuePredicate) {
                return fromValuePredicate(searchAtEntityClass, predicate)
            }
            log.warn("Unexpected predicate class: {}", predicate.javaClass)
            return null
        }
    }
}
