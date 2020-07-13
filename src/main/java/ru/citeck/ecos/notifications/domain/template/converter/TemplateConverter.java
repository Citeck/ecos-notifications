package ru.citeck.ecos.notifications.domain.template.converter;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto;
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate;
import ru.citeck.ecos.notifications.domain.template.entity.TemplateData;
import ru.citeck.ecos.notifications.domain.template.repository.NotificationTemplateRepository;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TemplateConverter {

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplate dtoToEntity(@NotNull NotificationTemplateDto dto) {
        NotificationTemplate entity = templateRepository.findOneByExtId(dto.getId()).orElse(new NotificationTemplate());
        entity.setName(dto.getName());
        entity.setNotificationTitle(Json.getMapper().toString(dto.getNotificationTitle()));
        entity.setExtId(dto.getId());

        Map<String, TemplateData> updatedData = new HashMap<>();

        dto.getData().forEach((lang, dataDto) -> {
            TemplateData td = entity.getData().get(lang);
            if (td == null) {
                td = new TemplateData();
            }
            td.setLang(lang);
            td.setName(dataDto.getName());
            td.setData(dataDto.getData());
            td.setTemplate(entity);

            updatedData.put(lang, td);
        });

        entity.setData(updatedData);

        return entity;
    }

    public NotificationTemplateDto entityToDto(@NotNull NotificationTemplate entity) {
        NotificationTemplateDto dto = new NotificationTemplateDto();

        dto.setId(entity.getExtId());
        dto.setName(entity.getName());
        dto.setNotificationTitle(Json.getMapper().read(entity.getNotificationTitle(), MLText.class));

        Map<String, TemplateDataDto> data = new HashMap<>();
        entity.getData().forEach((lang, templateData) -> {
            TemplateDataDto dataDto = new TemplateDataDto();
            dataDto.setName(templateData.getName());
            dataDto.setData(templateData.getData());

            data.put(lang, dataDto);
        });

        dto.setData(data);

        return dto;
    }

}
