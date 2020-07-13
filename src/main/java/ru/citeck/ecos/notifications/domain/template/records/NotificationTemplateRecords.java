package ru.citeck.ecos.notifications.domain.template.records;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.util.List;

@Component
public class NotificationTemplateRecords extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<NotificationTemplateDto>,
    LocalRecordsMetaDao<NotificationTemplateDto>,
    MutableRecordsLocalDao<NotificationTemplateDto> {

    public static final String ID = "notifications";

    private final NotificationTemplateService templateService;

    public NotificationTemplateRecords(NotificationTemplateService templateService) {
        setId(ID);
        this.templateService = templateService;
    }


    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        return null;
    }

    @Override
    public RecordsQueryResult<NotificationTemplateDto> queryLocalRecords(RecordsQuery query, MetaField field) {
        return null;
    }

    @Override
    public List<NotificationTemplateDto> getValuesToMutate(List<RecordRef> records) {
        return null;
    }

    @Override
    public RecordsMutResult save(List<NotificationTemplateDto> values) {
        return null;
    }

    @Override
    public List<NotificationTemplateDto> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        return null;
    }
}
