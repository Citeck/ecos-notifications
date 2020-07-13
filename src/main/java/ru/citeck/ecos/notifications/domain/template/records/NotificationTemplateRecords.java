package ru.citeck.ecos.notifications.domain.template.records;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class NotificationTemplateRecords extends LocalRecordsDao
    implements LocalRecordsMetaDao<NotificationTemplateDto>, MutableRecordsLocalDao<NotificationTemplateDto> {

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
    public List<NotificationTemplateDto> getValuesToMutate(List<RecordRef> records) {
        return getLocalRecordsMeta(records, null);
    }

    @Override
    public RecordsMutResult save(List<NotificationTemplateDto> values) {
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
    public List<NotificationTemplateDto> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        return records.stream()
            .map(RecordRef::getId)
            .map(templateService::findById)
            .map(opt -> opt.orElseGet(NotificationTemplateDto::new))
            .collect(Collectors.toList());
    }
}
