package ru.citeck.ecos.notifications.domain.template.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.notifications.domain.template.converter.TemplateConverter;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate;
import ru.citeck.ecos.notifications.domain.template.repository.NotificationTemplateRepository;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public List<NotificationTemplateDto> getAll(int max, int skip, Predicate predicate, Sort sort) {
        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        PageRequest page = PageRequest.of(skip / max, max, sort);

        return templateRepository.findAll(toSpec(predicate), page)
            .stream()
            .map(templateConverter::entityToDto)
            .collect(Collectors.toList());
    }

    private Specification<NotificationTemplate> toSpec(Predicate predicate) {

        if (predicate instanceof ValuePredicate) {

            ValuePredicate valuePred = (ValuePredicate) predicate;

            ValuePredicate.Type type = valuePred.getType();
            Object value = valuePred.getValue();
            String attribute = valuePred.getAttribute();

            if (RecordConstants.ATT_MODIFIED.equals(attribute)
                && ValuePredicate.Type.GT.equals(type)) {

                Instant instant = Json.getMapper().convert(value, Instant.class);
                if (instant != null) {
                    return (root, query, builder) ->
                        builder.greaterThan(root.get("lastModifiedDate").as(Instant.class), instant);
                }
            }
        }

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<NotificationTemplate> spec = null;

        if (org.apache.commons.lang3.StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<NotificationTemplate> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        return spec;
    }

    public long getCount(Predicate predicate) {
        Specification<NotificationTemplate> spec = toSpec(predicate);
        return spec != null ? (int) templateRepository.count(spec) : getCount();
    }

    public long getCount() {
        return templateRepository.count();
    }

    public List<NotificationTemplateDto> getAll(int max, int skip) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        PageRequest page = PageRequest.of(skip / max, max, sort);

        return templateRepository.findAll(page)
            .stream()
            .map(templateConverter::entityToDto)
            .collect(Collectors.toList());
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
    }
}
