package ru.citeck.ecos.notifications.domain.template.service

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import ru.citeck.ecos.notifications.domain.template.converter.TemplateConverter
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.repo.NotificationTemplateEntity
import ru.citeck.ecos.notifications.domain.template.repo.NotificationTemplateRepository
import ru.citeck.ecos.notifications.predicate.toValueModifiedSpec
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

// TODO: https://citeck.atlassian.net/browse/ECOSCOM-4811
@Service("domainNotificationTemplateService")
class NotificationTemplateService(
    private val templateRepository: NotificationTemplateRepository,
    private val templateConverter: TemplateConverter
) {

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

    fun findTemplateRefsForTypes(typeRefs: List<EntityRef>): List<EntityRef> {
        if (typeRefs.isEmpty()) {
            return emptyList()
        }

        val templates: MutableList<EntityRef> = ArrayList()

        templateRepository.findAllMultiTemplates().forEach { notificationTemplateEntity ->
            templateConverter.entityToDto(notificationTemplateEntity).multiTemplateConfig?.let { multiTemplates ->
                for ((template, type) in multiTemplates) {
                    if (EntityRef.isNotEmpty(type) &&
                        EntityRef.isNotEmpty(template) &&
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
                    EntityRef.isNotEmpty(template.template) || EntityRef.isNotEmpty(template.type)
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

        return templateRepository.findAll(toSpec(predicate), page)
            .map { entity: NotificationTemplateEntity ->
                templateConverter.entityToDto(
                    entity
                )
            }
            .toList()
    }

    fun getCount(predicate: Predicate): Long {
        val spec = toSpec<NotificationTemplateEntity>(predicate)
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
