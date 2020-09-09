package ru.citeck.ecos.notifications.domain.subscribe.api.records;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.notifications.domain.subscribe.dto.ActionDto;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDtoFactory;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.CustomDataEntity;
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService;
import ru.citeck.ecos.notifications.domain.subscribe.service.SubscriberService;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.error.ErrorUtils;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class SubscriptionActionRecords extends LocalRecordsDao implements LocalRecordsMetaDao<ActionDto>,
    MutableRecordsLocalDao<ActionDto> {

    private static final String ID = "subscription-action";

    private static final String PARAM_ACTION_UPDATE_CONFIG = "updateActionConfig";
    private static final String PARAM_SUBSCRIBER_ID = "subscriberId";
    private static final String PARAM_EVENT_TYPE = "eventType";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_ACTION_TYPE = "type";
    private static final String PARAM_ACTION_CONFIG = "config";
    private static final String PARAM_ACTION_CONDITION = "condition";
    private static final String PARAM_CUSTOM_DATA = "customData";

    private final SubscriberService subscriberService;
    private final SubscriberDtoFactory factory;
    private final ActionService actionService;

    {
        setId(ID);
    }

    public SubscriptionActionRecords(SubscriberService subscriberService, SubscriberDtoFactory factory,
                                     ActionService actionService) {
        this.subscriberService = subscriberService;
        this.factory = factory;
        this.actionService = actionService;
    }

    @NotNull
    @Override
    public List<ActionDto> getValuesToMutate(@NotNull List<RecordRef> list) {
        return getLocalRecordsMeta(list, null);
    }

    @NotNull
    @Override
    protected RecordsMutResult mutateImpl(@NotNull RecordsMutation mutation) {
        RecordsMutResult result = new RecordsMutResult();

        for (RecordMeta meta : mutation.getRecords()) {
            final String id = meta.getId().getId();
            ActionEntity resultAction;

            String updateConfig = meta.get(PARAM_ACTION_UPDATE_CONFIG) != null
                ? meta.get(PARAM_ACTION_UPDATE_CONFIG).asText() : "";

            if (StringUtils.isNotBlank(updateConfig)) {
                resultAction = processUpdateActionConfig(id, updateConfig);
            } else {
                resultAction = processMutate(id, meta);
            }

            RecordRef recordRef = RecordRef.create(meta.getId().getAppName(), meta.getId().getSourceId(),
                resultAction.getId().toString());

            result.addRecord(new RecordMeta(recordRef));
        }

        return result;
    }

    private ActionEntity processUpdateActionConfig(String id, String updateConfig) {
        ActionEntity action = actionService.findById(Long.valueOf(id))
            .orElseThrow(() -> new IllegalArgumentException(String.format("Action with id <%s> not found", id)));
        action.setConfigJSON(updateConfig);

        return actionService.save(action);
    }

    private ActionEntity processMutate(String id, RecordMeta meta) {
        ActionEntity resultAction;

        String subscriberId = meta.get(PARAM_SUBSCRIBER_ID).asText();
        if (StringUtils.isBlank(subscriberId)) {
            throwParamIsMandatory(PARAM_SUBSCRIBER_ID);
        }

        String eventType = meta.get(PARAM_EVENT_TYPE).asText();
        if (StringUtils.isBlank(eventType)) {
            throwParamIsMandatory(PARAM_EVENT_TYPE);
        }

        DataValue actionNode = meta.get(PARAM_ACTION);
        if (actionNode.isNull()) {
            throwParamIsMandatory(PARAM_ACTION);
        }

        String actionType = actionNode.get(PARAM_ACTION_TYPE).asText();
        if (StringUtils.isBlank(actionType)) {
            throwParamIsMandatory(PARAM_ACTION_TYPE);
        }

        String config = actionNode.get(PARAM_ACTION_CONFIG).isNotNull()
            ? actionNode.get(PARAM_ACTION_CONFIG).asText() : null;
        String condition = actionNode.get(PARAM_ACTION_CONDITION).isNotNull()
            ? actionNode.get(PARAM_ACTION_CONDITION).asText() : null;

        CustomDataEntity[] customData = new CustomDataEntity[0];

        DataValue customDataNode = actionNode.get(PARAM_CUSTOM_DATA);

        if (customDataNode.isNotNull()) {
            customData = Json.getMapper().convert(customDataNode, CustomDataEntity[].class);
        }

        if (StringUtils.isBlank(id)) {
            ActionEntity newAction = new ActionEntity();
            newAction.setType(NotificationType.valueOf(actionType));
            newAction.setConfigJSON(config);
            newAction.setCondition(condition);
            newAction.setCustomData(Arrays.asList(customData));

            resultAction = actionService.save(newAction);

            subscriberService.addActionToSubscriber(subscriberService.transformId(subscriberId), eventType,
                resultAction);
        } else {
            ActionEntity exists = actionService.findById(Long.valueOf(id))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Action with id <%s> not found", id)));
            exists.setType(NotificationType.valueOf(actionType));
            exists.setConfigJSON(config);
            exists.setCondition(condition);
            exists.setCustomData(Arrays.asList(customData));

            actionService.save(exists);

            resultAction = exists;
        }

        return resultAction;
    }

    private void throwParamIsMandatory(String param) {
        throw new IllegalArgumentException(String.format("Param <%s> is mandatory", param));
    }

    @Override
    public RecordsMutResult save(@NotNull List<ActionDto> list) {
        return null;
    }

    @Override
    public RecordsDelResult delete(@NotNull RecordsDeletion recordsDeletion) {
        RecordsDelResult result = new RecordsDelResult();

        recordsDeletion.getRecords().forEach(ref -> {
            try {
                actionService.deleteById(Long.valueOf(ref.getId()));
                result.addRecord(new RecordMeta(ref));
            } catch (EmptyResultDataAccessException e) {
                result.addError(ErrorUtils.convertException(e));
            }

        });

        return result;
    }

    @Override
    public List<ActionDto> getLocalRecordsMeta(@NotNull List<RecordRef> list, MetaField metaField) {
        return getValues(list);
    }

    private List<ActionDto> getValues(List<RecordRef> list) {
        return list.stream()
            .map(RecordRef::getId)
            .map(id ->
                Optional.of(id)
                    .filter(str -> !str.isEmpty())
                    .map(x -> actionService.findById(Long.valueOf(x))
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Subscriber with id <%s> not found!", id))))
                    .orElseGet(ActionEntity::new))
            .map(factory::fromAction)
            .collect(Collectors.toList());
    }
}
