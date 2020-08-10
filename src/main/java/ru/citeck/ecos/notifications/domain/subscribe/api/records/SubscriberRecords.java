package ru.citeck.ecos.notifications.domain.subscribe.api.records;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDto;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDtoFactory;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriberEntity;
import ru.citeck.ecos.notifications.domain.subscribe.service.SubscriberService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.CrudRecordsDAO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class SubscriberRecords extends CrudRecordsDAO<SubscriberDto> {

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

    @Override
    public List<SubscriberDto> getValuesToMutate(List<RecordRef> records) {
        return getValues(records);
    }

    @Override
    public List<SubscriberDto> getMetaValues(List<RecordRef> records) {
        return getValues(records);
    }

    private List<SubscriberDto> getValues(List<RecordRef> records) {
        return records.stream()
            .map(RecordRef::getId)
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

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {
        return null;
    }

    @Override
    public RecordsMutResult save(List<SubscriberDto> list) {
        return null;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        RecordsDelResult result = new RecordsDelResult();

        recordsDeletion.getRecords().forEach(ref -> {
            subscriberService.deleteSubscriber(subscriberService.transformId(ref.getId()));
            result.addRecord(new RecordMeta(ref));
        });

        return result;
    }

    @Override
    public RecordsQueryResult<SubscriberDto> getMetaValues(RecordsQuery recordsQuery) {
        return null;
    }
}