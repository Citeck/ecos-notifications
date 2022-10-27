package ru.citeck.ecos.notifications.domain.template.service

import mu.KotlinLogging
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.template.converter.TemplateConverter
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.repo.NotificationTemplateEntity
import ru.citeck.ecos.notifications.domain.template.repo.NotificationTemplateRepository
import ru.citeck.ecos.notifications.predicate.toValueModifiedSpec
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.*
import java.lang.reflect.Field
import java.time.DateTimeException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Service("domainNotificationTemplateService")
class NotificationTemplateService(
    private val templateRepository: NotificationTemplateRepository,
    private val templateConverter: TemplateConverter
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private var listener: Consumer<NotificationTemplateWithMeta>? = null

    val count: Long
        get() = templateRepository.count()

    fun update(dto: NotificationTemplateWithMeta) {
        val template = templateRepository.save(
            templateConverter.dtoToEntity(dto)
        )
        if (listener != null) {
            listener!!.accept(templateConverter.entityToDto(template))
        }
    }

    fun deleteById(id: String?) {
        require(!StringUtils.isBlank(id)) { "Id parameter is mandatory for template deletion" }
        templateRepository.findOneByExtId(id)
            .ifPresent { entity: NotificationTemplateEntity -> templateRepository.delete(entity) }
    }

    fun findTemplateRefsForTypes(typeRefs: List<RecordRef>): List<RecordRef> {
        if (typeRefs.isEmpty()) {
            return emptyList()
        }

        val templates: MutableList<RecordRef> = ArrayList()

        templateRepository.findAllMultiTemplates().forEach { notificationTemplateEntity ->
            templateConverter.entityToDto(notificationTemplateEntity).multiTemplateConfig?.let { multiTemplates ->
                for ((template, type) in multiTemplates) {
                    if (RecordRef.isNotEmpty(type) &&
                        RecordRef.isNotEmpty(template) &&
                        typeRefs.contains(type)
                    ) {
                        template?.let { templates.add(it) }
                    }
                }
            }
        }

        return templates
    }

    fun findById(id: String): Optional<NotificationTemplateWithMeta> {
        return if (StringUtils.isBlank(id)) {
            Optional.empty()
        } else templateRepository.findOneByExtId(id)
            .map { entity: NotificationTemplateEntity ->
                templateConverter.entityToDto(
                    entity
                )
            }
    }

    fun save(dto: NotificationTemplateWithMeta): NotificationTemplateWithMeta {
        if (CollectionUtils.isNotEmpty(dto.multiTemplateConfig)) {
            val notEmptyMultiTemplateConfigs: MutableList<MultiTemplateElementDto> = ArrayList()
            for (template in dto.multiTemplateConfig!!) {
                if (template.condition != null && template.condition !is VoidPredicate ||
                    RecordRef.isNotEmpty(template.template) || RecordRef.isNotEmpty(template.type)
                ) {
                    notEmptyMultiTemplateConfigs.add(template)
                }
            }
            if (notEmptyMultiTemplateConfigs.size != dto.multiTemplateConfig!!.size) {
                dto.multiTemplateConfig = notEmptyMultiTemplateConfigs
            }
        }

        if (CollectionUtils.isEmpty(dto.multiTemplateConfig)) {
            Assert.notEmpty(dto.templateData, "Template content must be defined")
        }

        if (StringUtils.isBlank(dto.id)) {
            dto.id = UUID.randomUUID().toString()
        }

        val saved = templateRepository.save(
            templateConverter.dtoToEntity(
                dto
            )
        )
        val resultDto = templateConverter.entityToDto(saved)
        if (listener != null) {
            listener!!.accept(resultDto)
        }

        return resultDto
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<NotificationTemplateWithMeta> {
        val sorting = sort ?: Sort.by(Sort.Direction.DESC, "id")

        val page = PageRequest.of(skip / max, max, sorting)

        val specification: Specification<NotificationTemplateEntity>? = specificationFromPredicate(predicate)
        return templateRepository.findAll(specification, page)
            .map { entity: NotificationTemplateEntity ->
                templateConverter.entityToDto(
                    entity
                )
            }
            .toList()
    }

    fun getCount(predicate: Predicate): Long {
        val spec = specificationFromPredicate(predicate)
        return if (spec != null) templateRepository.count(spec) else count
    }

    fun getAll(max: Int, skip: Int): List<NotificationTemplateWithMeta> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sort)
        return templateRepository.findAll(page)
            .stream()
            .map { entity: NotificationTemplateEntity ->
                templateConverter.entityToDto(
                    entity
                )
            }
            .collect(Collectors.toList())
    }

    fun addListener(listener: Consumer<NotificationTemplateWithMeta>) {
        this.listener = listener
    }

    private fun specificationFromPredicate(predicate: Predicate?): Specification<NotificationTemplateEntity>? {
        if (predicate == null) {
            return null
        }
        var result: Specification<NotificationTemplateEntity>? = null
        if (predicate is ComposedPredicate) {
            val specifications: ArrayList<Specification<NotificationTemplateEntity>> =
                ArrayList<Specification<NotificationTemplateEntity>>()
            predicate.getPredicates().forEach(
                Consumer { subPredicate: Predicate? ->
                    var subSpecification: Specification<NotificationTemplateEntity>? = null
                    if (subPredicate is ValuePredicate) {
                        subSpecification = fromValuePredicate(subPredicate)
                    } else if (subPredicate is ComposedPredicate) {
                        subSpecification = specificationFromPredicate(subPredicate)
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
            return fromValuePredicate(predicate)
        }
        log.warn("Unexpected predicate class: {}", predicate.javaClass)
        return null
    }

    private fun fromValuePredicate(valuePredicate: ValuePredicate): Specification<NotificationTemplateEntity>? {
        //ValuePredicate.Type.IN was not implemented
        if (StringUtils.isBlank(valuePredicate.getAttribute())) {
            return null
        }
        val attributeName = NotificationTemplateEntity.replaceNameValid(StringUtils.trim(valuePredicate.getAttribute()))
        if (NotificationTemplateEntity.isAttributeNameNotValid(attributeName)) {
            return null
        }

        var specification: Specification<NotificationTemplateEntity>? = null
        if (ValuePredicate.Type.CONTAINS == valuePredicate.getType()
            || ValuePredicate.Type.LIKE == valuePredicate.getType()
        ) {
            if (canSearchAsString(attributeName)) {
                val attributeValue = "%" + valuePredicate.getValue().asText().lowercase() + "%"
                specification = Specification { root: Root<NotificationTemplateEntity>,
                                                _: CriteriaQuery<*>?,
                                                builder: CriteriaBuilder ->
                    builder.like(builder.lower(root.get(attributeName)), attributeValue)
                }
            } else {
                return null
            }
        } else {
            val objectValue: Comparable<*>? = getObjectValue(attributeName, valuePredicate.getValue().asText())
            if (objectValue != null) {
                if (ValuePredicate.Type.EQ == valuePredicate.getType()) {
                    specification = Specification { root: Root<NotificationTemplateEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.equal(root.get<Any>(attributeName), objectValue)
                    }
                } else if (ValuePredicate.Type.GT == valuePredicate.getType()) {
                    specification = Specification { root: Root<NotificationTemplateEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.greaterThan<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                } else if (ValuePredicate.Type.GE == valuePredicate.getType()) {
                    specification = Specification { root: Root<NotificationTemplateEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.greaterThanOrEqualTo<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                } else if (ValuePredicate.Type.LT == valuePredicate.getType()) {
                    specification = Specification { root: Root<NotificationTemplateEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.lessThan<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                } else if (ValuePredicate.Type.LE == valuePredicate.getType()) {
                    specification = Specification { root: Root<NotificationTemplateEntity>,
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

    private fun canSearchAsString(attributeName: String): Boolean {
        var searchField = getTemplateField(attributeName)
        if (searchField == null) {
            return false
        }
        if (searchField.type == Instant::class.java || searchField.type == java.util.Date::class.java) {
            return false
        }
        return true
    }

    private fun getTemplateField(attributeName: String): Field? {
        var searchField: Field? = null
        try {
            searchField = NotificationTemplateEntity::class.java.getDeclaredField(attributeName)
        } catch (e: NoSuchFieldException) {
            var superclass: Class<in Nothing> = NotificationTemplateEntity::class.java.superclass
            while (searchField == null) {
                searchField = superclass.getDeclaredField(attributeName)
                superclass = superclass::class.java.superclass
            }
        }
        return searchField
    }

    private fun getObjectValue(attributeName: String, attributeValue: String): Comparable<*>? {
        var searchField = getTemplateField(attributeName)
        if (searchField != null)
            try {
                when (searchField.type) {
                    java.lang.String::class.java, String::class.java -> {
                        return attributeValue
                    }

                    java.lang.Long::class.java, Long::class.java -> {
                        return attributeValue.toLong()
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

    private fun <T> toSpec(pred: Predicate): Specification<T>? {
        pred.toValueModifiedSpec<T>()?.let { return it }

        var spec: Specification<T>? = null

        fun toLikeSpec(value: String, attName: String) {
            if (value.isNotBlank()) {
                val likeSpec = Specification { root: Root<T>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get(attName)),
                        "%" + value.lowercase() + "%"
                    )
                }

                spec = spec?.and(likeSpec) ?: likeSpec
            }
        }

        val predicateDto = PredicateUtils.convertToDto(pred, NotificationTemplatePredicateDto::class.java)

        toLikeSpec(predicateDto.record, "record")
        toLikeSpec(predicateDto.moduleId, "extId")
        toLikeSpec(predicateDto.tags, "tags")

        return spec
    }

    data class NotificationTemplatePredicateDto(
        var record: String = "",
        var moduleId: String = "",
        var tags: String = ""
    )
}
