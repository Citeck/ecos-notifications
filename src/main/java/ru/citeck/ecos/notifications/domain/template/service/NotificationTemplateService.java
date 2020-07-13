package ru.citeck.ecos.notifications.domain.template.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.notifications.domain.template.converter.TemplateConverter;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate;
import ru.citeck.ecos.notifications.domain.template.repository.NotificationTemplateRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final TemplateConverter templateConverter;

    public void update(@NotNull NotificationTemplateDto dto) {
        templateRepository.save(templateConverter.dtoToEntity(dto));
    }

    public void deleteById(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Id parameter is mandatory for template deletion");
        }
        templateRepository.findOneByExtId(id).ifPresent(templateRepository::delete);
    }

    public Optional<NotificationTemplateDto> findById(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return templateRepository.findOneByExtId(id)
            .map(templateConverter::entityToDto);
    }

    public NotificationTemplateDto save(NotificationTemplateDto dto) {
        if (StringUtils.isBlank(dto.getId())) {
            dto.setId(UUID.randomUUID().toString());
        }
        NotificationTemplate saved = templateRepository.save(templateConverter.dtoToEntity(dto));
        return templateConverter.entityToDto(saved);
    }
}
