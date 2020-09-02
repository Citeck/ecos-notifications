package ru.citeck.ecos.notifications.domain.file.service

import lombok.Data
import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.notifications.domain.file.converter.FileConverter
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.repo.FileEntity
import ru.citeck.ecos.notifications.domain.file.repo.FileRepository
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import java.time.Instant
import java.util.*
import java.util.stream.Collectors
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Service
class FileService(val fileRepository: FileRepository,
                  val fileConverter: FileConverter) {

    fun deleteById(id: String) {
        if (StringUtils.isBlank(id)) {
            throw IllegalArgumentException("Id parameter is mandatory for template file deletion")
        }
        fileRepository.findOneByExtId(id).ifPresent { entity: FileEntity ->
            fileRepository.delete(entity)
        }
    }

    fun findById(id: String): Optional<FileWithMeta> {
        return if (StringUtils.isBlank(id)) {
            Optional.empty()
        } else fileRepository.findOneByExtId(id)
            .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
    }

    fun save(dto: FileWithMeta): FileWithMeta {
        if (StringUtils.isBlank(dto.id)) {
            dto.id = UUID.randomUUID().toString()
        }
        val saved: FileEntity = fileRepository.save(fileConverter.dtoToEntity(dto))
        return fileConverter.entityToDto(saved)
    }

    fun getCount(predicate: Predicate): Long {
        val spec: Specification<FileEntity> = toSpec(predicate) ?: return 0
        return (fileRepository.count(spec).toInt()).toLong()
    }

    fun getCount(): Long {
        return fileRepository.count()
    }

    private fun toSpec(predicate: Predicate): Specification<FileEntity>? {
        if (predicate is ValuePredicate) {
            val type = predicate.type
            val value = predicate.value
            val attribute = predicate.attribute
            if (RecordConstants.ATT_MODIFIED == attribute && ValuePredicate.Type.GT == type) {
                val instant = mapper.convert(value, Instant::class.java)
                if (instant != null) {
                    return Specification { root: Root<FileEntity>,
                                           _: CriteriaQuery<*>,
                                           builder: CriteriaBuilder ->
                        builder.greaterThan(
                            root.get<Any>("lastModifiedDate").`as`(Instant::class.java), instant
                        )
                    }
                }
            }
        }
        val predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto::class.java)
        var spec: Specification<FileEntity>? = null
        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = Specification { root: Root<FileEntity>,
                                   _: CriteriaQuery<*>?,
                                   builder: CriteriaBuilder ->
                builder.like(
                    builder.lower(root.get("name")), "%" + predicateDto.name!!.toLowerCase() + "%"
                )
            }
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            val idSpec = Specification { root: Root<FileEntity?>, _: CriteriaQuery<*>?,
                                         builder: CriteriaBuilder ->
                builder.like(
                    builder.lower(root.get("extId")), "%" + predicateDto.moduleId!!.toLowerCase() + "%"
                )
            }
            spec = spec?.or(idSpec)
        }
        return spec
    }

    fun getAll(max: Int, skip: Int): List<FileWithMeta> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sort)
        return fileRepository.findAll(page)
            .stream()
            .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
            .collect(Collectors.toList())
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<FileWithMeta> {
        var sort = sort
        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id")
        }
        val page = PageRequest.of(skip / max, max, sort)
        return fileRepository.findAll(toSpec(predicate), page)
            .stream()
            .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
            .collect(Collectors.toList())
    }

    @Data
    open class PredicateDto {
        val name: String? = null
        val moduleId: String? = null
    }
}
