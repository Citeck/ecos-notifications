package ru.citeck.ecos.notifications.domain.template.service;

import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.notifications.domain.template.converter.TemplateConverter;
import ru.citeck.ecos.notifications.domain.template.dto.MultiTemplateElementDto;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta;
import ru.citeck.ecos.notifications.domain.template.repo.NotificationTemplateEntity;
import ru.citeck.ecos.notifications.domain.template.repo.NotificationTemplateRepository;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final TemplateConverter templateConverter;

    private Consumer<NotificationTemplateWithMeta> listener;


    public NotificationTemplateService(NotificationTemplateRepository templateRepository,
                                       TemplateConverter templateConverter) {
        this.templateRepository = templateRepository;
        this.templateConverter = templateConverter;
    }

    public void update(@NotNull NotificationTemplateWithMeta dto) {
        NotificationTemplateEntity template = templateRepository.save(templateConverter.dtoToEntity(dto));
        if (listener != null) {
            listener.accept(templateConverter.entityToDto(template));
        }
    }

    public void deleteById(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Id parameter is mandatory for template deletion");
        }
        templateRepository.findOneByExtId(id).ifPresent(templateRepository::delete);
    }

    @NotNull
    public List<RecordRef> findTemplateRefsForTypes(@NotNull List<RecordRef> typeRefs) {

        if (typeRefs.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecordRef> templates = new ArrayList<>();

        List<NotificationTemplateEntity> multiTemplates = templateRepository.findAllMultiTemplates();

        for (NotificationTemplateEntity template : multiTemplates) {
            NotificationTemplateWithMeta templateDto = templateConverter.entityToDto(template);
            if (templateDto.getMultiTemplateConfig() != null) {
                for (MultiTemplateElementDto configDto : templateDto.getMultiTemplateConfig()) {

                    if (RecordRef.isNotEmpty(configDto.getType())
                            && RecordRef.isNotEmpty(configDto.getTemplate())
                            && typeRefs.contains(configDto.getType())) {

                        templates.add(configDto.getTemplate());
                    }
                }
            }
        }

        return templates;
    }

    public Optional<NotificationTemplateWithMeta> findById(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return templateRepository.findOneByExtId(id)
            .map(templateConverter::entityToDto);
    }

    public NotificationTemplateWithMeta save(NotificationTemplateWithMeta dto) {
        if (dto != null && !CollectionUtils.isEmpty(dto.getMultiTemplateConfig())) {
            List<MultiTemplateElementDto> checkedList = new ArrayList<>();
            for (MultiTemplateElementDto template : dto.getMultiTemplateConfig()) {
                if (template.getCondition() != null && !(template.getCondition() instanceof VoidPredicate)
                    || template.getTemplate() != null && RecordRef.isNotEmpty(template.getTemplate())
                    || template.getType() != null && RecordRef.isNotEmpty(template.getType())) {
                    checkedList.add(template);
                }
            }
            if (checkedList.size() != dto.getMultiTemplateConfig().size()) {
                dto.setMultiTemplateConfig(checkedList);
            }
        }
        if (dto != null && CollectionUtils.isEmpty(dto.getMultiTemplateConfig()))
            Assert.notEmpty(dto.getTemplateData(), "Template content must be defined");
        if (StringUtils.isBlank(dto.getId())) {
            dto.setId(UUID.randomUUID().toString());
        }
        NotificationTemplateEntity saved = templateRepository.save(templateConverter.dtoToEntity(dto));
        NotificationTemplateWithMeta resultDto = templateConverter.entityToDto(saved);
        if (listener != null) {
            listener.accept(resultDto);
        }
        return resultDto;
    }

    public List<NotificationTemplateWithMeta> getAll(int max, int skip, Predicate predicate, Sort sort) {
        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        PageRequest page = PageRequest.of(skip / max, max, sort);

        return templateRepository.findAll(toSpec(predicate), page)
            .stream()
            .map(templateConverter::entityToDto)
            .collect(Collectors.toList());
    }

    private Specification<NotificationTemplateEntity> toSpec(Predicate predicate) {

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
        Specification<NotificationTemplateEntity> spec = null;

        if (org.apache.commons.lang3.StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<NotificationTemplateEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        return spec;
    }

    public long getCount(Predicate predicate) {
        Specification<NotificationTemplateEntity> spec = toSpec(predicate);
        return spec != null ? (int) templateRepository.count(spec) : getCount();
    }

    public long getCount() {
        return templateRepository.count();
    }

    public List<NotificationTemplateWithMeta> getAll(int max, int skip) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        PageRequest page = PageRequest.of(skip / max, max, sort);

        return templateRepository.findAll(page)
            .stream()
            .map(templateConverter::entityToDto)
            .collect(Collectors.toList());
    }

    public void addListener(Consumer<NotificationTemplateWithMeta> listener) {
        this.listener = listener;
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
    }
}
