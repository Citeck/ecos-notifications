package ru.citeck.ecos.notifications.domain.template.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto;
import ru.citeck.ecos.notifications.domain.template.dto.TemplateModule;
import ru.citeck.ecos.notifications.domain.template.entity.TemplateData;
import ru.citeck.ecos.notifications.domain.template.repository.NotificationTemplateRepository;
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateService(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public void update(@NotNull NotificationTemplateDto dto) {
        templateRepository.save(toEntity(dto));
    }

    private NotificationTemplate toEntity(@NotNull NotificationTemplateDto dto) {
        NotificationTemplate entity = templateRepository.findOneByExtId(dto.getId()).orElse(new NotificationTemplate());
        entity.setTitle(dto.getTitle());
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

}
