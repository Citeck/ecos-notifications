package ru.citeck.ecos.notifications.domain.subscribe.api.records;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDto;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDtoFactory;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriberEntity;
import ru.citeck.ecos.notifications.domain.subscribe.service.SubscriberService;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Roman Makarskiy
 */
@Component
public class SubscriberRecords extends AbstractRecordsDao implements RecordAttsDao,
    RecordMutateDtoDao<SubscriberDto>, RecordsDeleteDao {

    private static final String ID = "subscribers";

    private final SubscriberService subscriberService;
    private final SubscriberDtoFactory factory;

    public SubscriberRecords(SubscriberService subscriberService, SubscriberDtoFactory factory) {
        this.subscriberService = subscriberService;
        this.factory = factory;
    }

    @Override
    public SubscriberDto getRecToMutate(@NotNull String recordId) {
        return getRecordAtts(recordId);
    }

    @NotNull
    @Override
    public String saveMutatedRec(SubscriberDto subscriberDto) {
        return subscriberDto.getId();
    }

    @NotNull
    @Override
    public List<DelStatus> delete(@NotNull List<String> recordIds) {
        List<DelStatus> result = new ArrayList<>();
        for (String recordId : recordIds) {
            subscriberService.deleteSubscriber(subscriberService.transformId(recordId));
            result.add(DelStatus.OK);
        }
        return result;
    }

    @Nullable
    @Override
    public SubscriberDto getRecordAtts(@NotNull String recordId) {
        return getValue(recordId);
    }

    private SubscriberDto getValue(String recordId) {

        SubscriberEntity entity = Optional.of(recordId)
                    .filter(str -> !str.isEmpty())
                    .map(x -> subscriberService.getById(x)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Subscriber with id <%s> not found!", recordId))))
                    .orElseGet(SubscriberEntity::new);

        return factory.fromSubscriber(entity);
    }

    @NotNull
    @Override
    public String getId() {
        return ID;
    }
}
