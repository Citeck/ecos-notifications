package ru.citeck.ecos.notifications.domain.sender.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.notifications.domain.sender.converter.toDto
import ru.citeck.ecos.notifications.domain.sender.converter.toDtoWithMeta
import ru.citeck.ecos.notifications.domain.sender.converter.toEntity
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDto
import ru.citeck.ecos.notifications.domain.sender.dto.NotificationsSenderDtoWithMeta
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderEntity
import ru.citeck.ecos.notifications.domain.sender.repo.NotificationsSenderRepository
import ru.citeck.ecos.records2.predicate.model.*
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.lang.reflect.Field
import java.util.function.BiConsumer
import java.util.function.Consumer
import javax.annotation.PostConstruct

@Service
class NotificationsSenderServiceImpl(
    val repository: NotificationsSenderRepository,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory
) : NotificationsSenderService {

    companion object {
        private val log = KotlinLogging.logger {}
        private var attributeNamesSet: Set<String> = emptySet()
        private val attributeNames: Set<String>
            get() {
                if (attributeNamesSet.isEmpty()) {
                    attributeNamesSet = NotificationsSenderEntity::class.java.declaredFields
                        .map { attribute -> attribute.name }
                        .toSet()
                }
                return attributeNamesSet
            }
    }

    private lateinit var searchConv: JpaSearchConverter<NotificationsSenderEntity>

    private val changeListeners: MutableList<BiConsumer<NotificationsSenderDto?, NotificationsSenderDto?>> =
        arrayListOf()

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(NotificationsSenderEntity::class.java).build()
    }

    override fun getSenderById(id: String): NotificationsSenderDtoWithMeta? {
        if (id.isBlank()) {
            return null
        }
        val found = repository.findOneByExtId(id)

        return if (found.isPresent) {
            NotificationsSenderDtoWithMeta(found.get().toDto())
        } else {
            null
        }
    }

    @Transactional
    override fun delete(id: String) {
        repository.findOneByExtId(id).ifPresent(repository::delete)
    }

    @Transactional
    override fun removeAllByExtId(extIds: List<String>) {
        repository.deleteAllByExtIdIn(extIds)
    }

    @Transactional
    override fun save(senderDto: NotificationsSenderDto): NotificationsSenderDtoWithMeta {
        val beforeSenderDto = senderDto.id?.let { getSenderById(it)?.toDto() }
        val entity = repository.save(senderDto.toEntity())
        val afterSenderDto = entity.toDto()

        for (listener in changeListeners) {
            listener.accept(beforeSenderDto, afterSenderDto)
        }
        return NotificationsSenderDtoWithMeta(afterSenderDto)
    }

    override fun onSenderChanged(listener: BiConsumer<NotificationsSenderDto?, NotificationsSenderDto?>) {
        changeListeners.add(listener)
    }

    override fun getCount(): Long {
        return repository.count()
    }

    override fun getCount(predicate: Predicate): Long {
        return searchConv.getCount(repository, predicate)
    }

    override fun getAll(): List<NotificationsSenderDtoWithMeta> {
        return repository.findAll().map { it.toDtoWithMeta() }.toList()
    }

    override fun getAllEnabled(): List<NotificationsSenderDtoWithMeta> {
        return getEnabled(null, null)
    }

    override fun getEnabled(predicate: Predicate?, sort: Sort?): List<NotificationsSenderDtoWithMeta> {
        val page = PageRequest.of(
            0,
            10000,
            sort ?: Sort.by(Sort.Direction.ASC, NotificationsSenderEntity.PROP_ORDER)
        )
        val enablePredicate = Predicates.eq(NotificationsSenderEntity.PROP_ENABLED, true)
        val specification = if (predicate == null) {
            toSpecification(enablePredicate)
        } else {
            toSpecification(Predicates.and(enablePredicate, predicate))
        }
        return repository.findAll(specification, page)
            .map { it.toDtoWithMeta() }
            .toList()
    }

    override fun getAll(maxItems: Int, skipCount: Int, predicate: Predicate, sort: List<SortBy>): List<NotificationsSenderDtoWithMeta> {
        return searchConv.findAll(repository, predicate, maxItems, skipCount, sort)
            .map { it.toDtoWithMeta() }
            .toList()
    }

    fun toSpecification(predicate: Predicate?): Specification<NotificationsSenderEntity>? {
        if (predicate == null) {
            return null
        }
        var result: Specification<NotificationsSenderEntity>? = null
        when (predicate) {
            is ComposedPredicate -> {
                val specifications = ArrayList<Specification<NotificationsSenderEntity>>()
                predicate.getPredicates().forEach(
                    Consumer { subPredicate: Predicate? ->
                        var subSpecification: Specification<NotificationsSenderEntity>? = null
                        if (subPredicate is ValuePredicate) {
                            subSpecification = fromValuePredicate((subPredicate as ValuePredicate?)!!)
                        } else if (subPredicate is ComposedPredicate) {
                            subSpecification = toSpecification(subPredicate)
                        }
                        if (subSpecification != null) {
                            specifications.add(subSpecification)
                        }
                    }
                )
                if (specifications.isNotEmpty()) {
                    result = specifications[0]
                    if (specifications.size > 1) {
                        for (idx in 1 until specifications.size) {
                            result = if (predicate is AndPredicate) {
                                result!!.and(specifications[idx])
                            } else {
                                result!!.or(specifications[idx])
                            }
                        }
                    }
                }
                return result
            }
            is ValuePredicate -> {
                return fromValuePredicate((predicate as ValuePredicate?)!!)
            }
            else -> {
                log.warn("Unexpected predicate class: {}", predicate.javaClass)
            }
        }
        return null
    }

    private fun fromValuePredicate(valuePredicate: ValuePredicate): Specification<NotificationsSenderEntity>? {
        // ValuePredicate.Type.IN was not implemented
        if (StringUtils.isBlank(valuePredicate.getAttribute())) {
            return null
        }
        val attributeName = StringUtils.trim(valuePredicate.getAttribute())
        if (!attributeNames.contains(attributeName)) {
            return null
        }
        var specification: Specification<NotificationsSenderEntity>? = null
        if (ValuePredicate.Type.CONTAINS == valuePredicate.getType() ||
            ValuePredicate.Type.LIKE == valuePredicate.getType()
        ) {
            val recordRef = EntityRef.valueOf(valuePredicate.getValue().asText())
            val tmpValue = if (EntityRef.isEmpty(recordRef)) {
                valuePredicate.getValue().asText()
            } else {
                recordRef.getLocalId()
            }
            val attributeValue = if (ValuePredicate.Type.CONTAINS == valuePredicate.getType()) {
                "%" + tmpValue.lowercase() + "%"
            } else {
                tmpValue.lowercase()
            }
            specification = Specification<NotificationsSenderEntity> { root, query, builder ->
                builder.like(builder.lower(root.get(attributeName)), attributeValue)
            }
        } else {
            val objectValue = getObjectValue(attributeName, valuePredicate.getValue().asText())
            if (objectValue != null) {
                @Suppress("UNCHECKED_CAST")
                val comparableValue = objectValue as Comparable<Comparable<*>>

                if (ValuePredicate.Type.EQ == valuePredicate.getType()) {
                    specification = Specification<NotificationsSenderEntity> { root, query, builder ->
                        builder.equal(root.get<Any>(attributeName), objectValue)
                    }
                } else if (ValuePredicate.Type.GT == valuePredicate.getType()) {
                    specification = Specification<NotificationsSenderEntity> { root, query, builder ->
                        builder.greaterThan(root.get(attributeName), comparableValue)
                    }
                } else if (ValuePredicate.Type.GE == valuePredicate.getType()) {
                    specification = Specification<NotificationsSenderEntity> { root, query, builder ->
                        builder.greaterThanOrEqualTo(root.get(attributeName), comparableValue)
                    }
                } else if (ValuePredicate.Type.LT == valuePredicate.getType()) {
                    specification = Specification<NotificationsSenderEntity> { root, query, builder ->
                        builder.lessThan(root.get(attributeName), comparableValue)
                    }
                } else if (ValuePredicate.Type.LE == valuePredicate.getType()) {
                    specification = Specification<NotificationsSenderEntity> { root, query, builder ->
                        builder.lessThanOrEqualTo(root.get(attributeName), comparableValue)
                    }
                }
            }
        }
        return specification
    }

    private fun getObjectValue(attributeName: String, attributeValue: String): Comparable<*>? {
        try {
            val searchField: Field = NotificationsSenderEntity::class.java.getDeclaredField(attributeName)
            if (searchField.type.isEnum) {
                val enumClz = searchField.type.enumConstants as Array<Enum<*>>
                return enumClz.first { it.name == attributeValue }
            }
            when (searchField.type) {
                java.lang.String::class.java -> {
                    return attributeValue
                }
                java.lang.Float::class.java, Float::class.java -> {
                    return attributeValue.toFloat()
                }
                java.lang.Boolean::class.java, Boolean::class.java -> {
                    return attributeValue.toBoolean()
                }
                else -> {
                    log.error("Unexpected attribute type {} for predicate", searchField.type)
                }
            }
        } catch (e: NoSuchFieldException) {
            log.warn("Unexpected attribute: {}", attributeName)
        }
        return null
    }
}
