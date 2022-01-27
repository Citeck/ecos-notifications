package ru.citeck.ecos.notifications.domain.file.service

import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import ru.citeck.ecos.notifications.domain.file.converter.FileConverter
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.repo.FileEntity
import ru.citeck.ecos.notifications.domain.file.repo.FileRepository
import ru.citeck.ecos.notifications.predicate.toDefaultEntitySpec
import ru.citeck.ecos.records2.predicate.model.Predicate
import java.util.*
import java.util.stream.Collectors

@Service
class FileService(
    val fileRepository: FileRepository,
    val fileConverter: FileConverter
) {

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
        val spec: Specification<FileEntity> = predicate.toDefaultEntitySpec() ?: return 0
        return (fileRepository.count(spec).toInt()).toLong()
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

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<FileWithMeta> {
        val sortLocal = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sortLocal)
        return fileRepository.findAll(predicate.toDefaultEntitySpec(), page)
            .stream()
            .map { entity: FileEntity -> fileConverter.entityToDto(entity) }
            .collect(Collectors.toList())
    }

}
