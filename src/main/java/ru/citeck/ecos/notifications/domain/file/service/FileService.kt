package ru.citeck.ecos.notifications.domain.file.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.file.converter.FileConverter
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.repo.FileEntity
import ru.citeck.ecos.notifications.domain.file.repo.FileRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.util.*
import java.util.stream.Collectors
import javax.annotation.PostConstruct

@Service
class FileService(
    val fileRepository: FileRepository,
    val fileConverter: FileConverter,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory
) {

    private lateinit var searchConv: JpaSearchConverter<FileEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(FileEntity::class.java).build()
    }

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
        } else {
            fileRepository.findOneByExtId(id)
                .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
        }
    }

    fun save(dto: FileWithMeta): FileWithMeta {
        if (StringUtils.isBlank(dto.id)) {
            dto.id = UUID.randomUUID().toString()
        }
        val saved: FileEntity = fileRepository.save(fileConverter.dtoToEntity(dto))
        return fileConverter.entityToDto(saved)
    }

    fun getCount(predicate: Predicate): Long {
        return searchConv.getCount(fileRepository, predicate)
    }

    fun getCount(): Long {
        return fileRepository.count()
    }

    fun getAll(max: Int, skip: Int): List<FileWithMeta> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sort)
        return fileRepository.findAll(page)
            .stream()
            .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
            .collect(Collectors.toList())
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<FileWithMeta> {
        return searchConv.findAll(fileRepository, predicate, max, skip, sort)
            .stream()
            .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
            .collect(Collectors.toList())
    }
}
