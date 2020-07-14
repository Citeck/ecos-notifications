package ru.citeck.ecos.notifications.domain.template.converter

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate
import ru.citeck.ecos.notifications.domain.template.entity.TemplateData
import ru.citeck.ecos.notifications.domain.template.repository.NotificationTemplateRepository
import java.util.*

@Component
class TemplateConverter {

    @Autowired
    private lateinit var templateRepository: NotificationTemplateRepository

    fun dtoToEntity(dto: NotificationTemplateDto): NotificationTemplate {
        val entity = templateRepository.findOneByExtId(dto.id).orElse(NotificationTemplate())
        entity.name = dto.name
        entity.notificationTitle = mapper.toString(dto.notificationTitle)
        entity.extId = dto.id

        val updatedData: MutableMap<String, TemplateData> = HashMap()

        dto.data.forEach { (lang: String, dataDto: TemplateDataDto) ->
            var td = entity.data[lang]
            if (td == null) {
                td = TemplateData(
                    name = dataDto.name,
                    lang = lang,
                    data = dataDto.data,
                    template = entity
                )
            }
            updatedData[lang] = td
        }
        entity.data = updatedData
        return entity
    }

    fun entityToDto(entity: NotificationTemplate): NotificationTemplateDto {
        val dto = NotificationTemplateDto(
            id = entity.extId!!,
            name = entity.name,
            notificationTitle = mapper.read(entity.notificationTitle, MLText::class.java),
            creator = entity.createdBy,
            created = entity.createdDate,
            modifier = entity.lastModifiedBy,
            modified = entity.lastModifiedDate
        )

        val data: MutableMap<String, TemplateDataDto> = HashMap()

        entity.data.forEach { (lang: String, templateData: TemplateData) ->
            if (templateData.name != null && templateData.data != null) {
                val dataDto = TemplateDataDto(templateData.name!!, templateData.data!!)
                data[lang] = dataDto
            }
        }
        dto.data = data
        return dto
    }
}
