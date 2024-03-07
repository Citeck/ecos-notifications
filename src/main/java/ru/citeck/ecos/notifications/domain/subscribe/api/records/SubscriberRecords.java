package ru.citeck.ecos.notifications.domain.subscribe.api.records;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDto;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDtoFactory;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriberEntity;
import ru.citeck.ecos.notifications.domain.subscribe.service.SubscriberService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class SubscriberRecords extends LocalRecordsDao implements LocalRecordsMetaDao<SubscriberDto>,
    MutableRecordsLocalDao<SubscriberDto> {

    private static final String ID = "subscribers";

    {
        setId(ID);
    }

    private final SubscriberService subscriberService;
    private final SubscriberDtoFactory factory;

    public SubscriberRecords(SubscriberService subscriberService, SubscriberDtoFactory factory) {
        this.subscriberService = subscriberService;
        this.factory = factory;
    }

    @NotNull
    @Override
    public List<SubscriberDto> getValuesToMutate(@NotNull List<EntityRef> list) {
        return getLocalRecordsMeta(list, null);
    }

    @Override
    public RecordsMutResult save(@NotNull List<SubscriberDto> list) {
        return null;
    }

    @Override
    public RecordsDelResult delete(@NotNull RecordsDeletion recordsDeletion) {
        RecordsDelResult result = new RecordsDelResult();

        recordsDeletion.getRecords().forEach(ref -> {
            subscriberService.deleteSubscriber(subscriberService.transformId(ref.getLocalId()));
            result.addRecord(new RecordMeta(ref));
        });

        return result;
    }

    @Override
    public List<SubscriberDto> getLocalRecordsMeta(@NotNull List<EntityRef> list, MetaField metaField) {
        return getValues(list);
    }

    private List<SubscriberDto> getValues(List<EntityRef> records) {
        return records.stream()
            .map(EntityRef::getLocalId)
            .map(id ->
                Optional.of(id)
                    .filter(str -> !str.isEmpty())
                    .map(x -> subscriberService.getById(x)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Subscriber with id <%s> not found!", id))))
                    .orElseGet(SubscriberEntity::new))
            .map(factory::fromSubscriber)
            .collect(Collectors.toList());
    }
}
