package ru.citeck.ecos.notifications.domain.file.converter

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.file.dto.FileWithMeta
import ru.citeck.ecos.notifications.domain.file.repo.FileEntity
import ru.citeck.ecos.notifications.domain.file.repo.FileRepository

@Component
class FileConverter {

    @Autowired
    private lateinit var fileRepository: FileRepository

    fun dtoToEntity(dto: FileWithMeta): FileEntity {
        val entity = fileRepository.findOneByExtId(dto.id).orElse(FileEntity())
        entity.extId = dto.id
        entity.data = dto.data
        return entity
    }

    fun entityToDto(entity: FileEntity): FileWithMeta {
        return FileWithMeta(
            id = entity.extId!!,
            data = entity.data,
            creator = entity.createdBy,
            created = entity.createdDate,
            modifier = entity.lastModifiedBy,
            modified = entity.lastModifiedDate
        )
    }
}
