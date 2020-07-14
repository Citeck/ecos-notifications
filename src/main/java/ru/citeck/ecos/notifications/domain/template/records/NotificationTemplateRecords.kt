package ru.citeck.ecos.notifications.domain.template.records;

import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NotificationTemplateRecords extends LocalRecordsDao implements
    LocalRecordsQueryWithMetaDao<NotificationTemplateRecords.NotTemplateRecord>,
    LocalRecordsMetaDao<NotificationTemplateRecords.NotTemplateRecord>,
    MutableRecordsLocalDao<NotificationTemplateRecords.NotTemplateRecord> {

    public static final String ID = "template";

    private final NotificationTemplateService templateService;

    public NotificationTemplateRecords(NotificationTemplateService templateService) {
        setId(ID);
        this.templateService = templateService;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        RecordsDelResult result = new RecordsDelResult();
        for (RecordRef record : deletion.getRecords()) {
            templateService.deleteById(record.getId());
            result.addRecord(new RecordMeta(record));
        }
        return result;
    }

    @Override
    public List<NotTemplateRecord> getValuesToMutate(List<RecordRef> records) {
        return getLocalRecordsMeta(records, null);
    }

    @Override
    public RecordsMutResult save(List<NotTemplateRecord> values) {
        RecordsMutResult result = new RecordsMutResult();

        List<RecordMeta> savedList = values.stream()
            .map(templateService::save)
            .map(NotificationTemplateDto::getId)
            .map(RecordMeta::new)
            .collect(Collectors.toList());
        result.setRecords(savedList);

        return result;
    }

    @Override
    public List<NotTemplateRecord> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        return records.stream()
            .map(RecordRef::getId)
            .map(id -> templateService.findById(id)
                .orElseGet(() -> new NotificationTemplateDto(id)))
            .map(NotTemplateRecord::new)
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<NotTemplateRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {
        RecordsQueryResult<NotTemplateRecord> records = new RecordsQueryResult<>();

        int max = recordsQuery.getMaxItems();
        if (max <= 0) {
            max = 10000;
        }
        int skip = recordsQuery.getSkipCount();

        if ("predicate".equals(recordsQuery.getLanguage())) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            List<Sort.Order> order = recordsQuery.getSortBy()
                .stream()
                .filter(s -> RecordConstants.ATT_MODIFIED.equals(s.getAttribute()))
                .map(s -> {
                    String attribute = s.getAttribute();
                    if (RecordConstants.ATT_MODIFIED.equals(attribute)) {
                        attribute = "lastModifiedDate";
                    }
                    return s.isAscending() ? Sort.Order.asc(attribute) : Sort.Order.desc(attribute);
                })
                .collect(Collectors.toList());

            Collection<NotTemplateRecord> types = templateService.getAll(
                max,
                recordsQuery.getSkipCount(),
                predicate,
                !order.isEmpty() ? Sort.by(order) : null
            )
                .stream()
                .map(NotTemplateRecord::new)
                .collect(Collectors.toList());

            records.setRecords(new ArrayList<>(types));
            records.setTotalCount(templateService.getCount(predicate));
            return records;
        }

        if ("criteria".equals(recordsQuery.getLanguage())) {

            records.setRecords(new ArrayList<>(
                templateService.getAll(max, skip)
                    .stream()
                    .map(NotTemplateRecord::new)
                    .collect(Collectors.toList()))
            );
            records.setTotalCount(templateService.getCount());

            return records;
        }

        return new RecordsQueryResult<>();
    }

    @NoArgsConstructor
    public static class NotTemplateRecord extends NotificationTemplateDto {

        public NotTemplateRecord(NotificationTemplateDto dto) {
            super(dto);
        }

        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        @MetaAtt(".type")
        public RecordRef getEcosType() {
            return RecordRef.create("emodel", "type", "notification-template");
        }

        @MetaAtt(".disp")
        public String getDisplayName() {
            return getName();
        }

        //TODO: add template data
    }
}
