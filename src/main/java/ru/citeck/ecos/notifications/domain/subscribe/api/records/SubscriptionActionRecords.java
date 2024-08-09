package ru.citeck.ecos.notifications.domain.subscribe.api.records;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Sets;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Component
public class SubscriptionActionRecords extends AbstractRecordsDao implements RecordAttsDao,
    RecordMutateDao,
    RecordsDeleteDao {

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

    public SubscriptionActionRecords(SubscriberService subscriberService, SubscriberDtoFactory factory,
                                     ActionService actionService) {
        this.subscriberService = subscriberService;
        this.factory = factory;
        this.actionService = actionService;
    }

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts recordAtts) throws Exception {

        final String id = recordAtts.getId();
        ActionEntity resultAction;

        String updateConfig = recordAtts.getAtt(PARAM_ACTION_UPDATE_CONFIG).isNotNull()
            ? recordAtts.getAtt(PARAM_ACTION_UPDATE_CONFIG).toString() : "";

        if (StringUtils.isNotBlank(updateConfig)) {
            resultAction = processUpdateActionConfig(id, updateConfig);
        } else {
            resultAction = processMutate(id, recordAtts);
        }

        return resultAction.getId().toString();
    }

    private ActionEntity processUpdateActionConfig(String id, String updateConfig) {
        ActionEntity action = actionService.findById(Long.valueOf(id))
            .orElseThrow(() -> new IllegalArgumentException(String.format("Action with id <%s> not found", id)));
        action.setConfigJSON(updateConfig);

        return actionService.save(action);
    }

    private ActionEntity processMutate(String id, LocalRecordAtts meta) {
        ActionEntity resultAction;

        String subscriberId = meta.getAtt(PARAM_SUBSCRIBER_ID).asText();
        if (StringUtils.isBlank(subscriberId)) {
            throwParamIsMandatory(PARAM_SUBSCRIBER_ID);
        }

        String eventType = meta.getAtt(PARAM_EVENT_TYPE).asText();
        if (StringUtils.isBlank(eventType)) {
            throwParamIsMandatory(PARAM_EVENT_TYPE);
        }

        DataValue actionNode = meta.getAtt(PARAM_ACTION);
        if (actionNode.isNull()) {
            throwParamIsMandatory(PARAM_ACTION);
        }

        String actionType = actionNode.get(PARAM_ACTION_TYPE).asText();
        if (StringUtils.isBlank(actionType)) {
            throwParamIsMandatory(PARAM_ACTION_TYPE);
        }

        String config = actionNode.get(PARAM_ACTION_CONFIG).isNotNull()
            ? actionNode.get(PARAM_ACTION_CONFIG).toString() : null;
        String condition = actionNode.get(PARAM_ACTION_CONDITION).isNotNull()
            ? actionNode.get(PARAM_ACTION_CONDITION).asText() : null;

        CustomDataEntity[] customData = new CustomDataEntity[0];

        DataValue customDataNode = actionNode.get(PARAM_CUSTOM_DATA);

        if (customDataNode.isNotNull()) {
            customData = Json.getMapper().convertNotNull(customDataNode, CustomDataEntity[].class);
        }

        if (StringUtils.isBlank(id)) {
            ActionEntity newAction = new ActionEntity();
            newAction.setType(NotificationType.valueOf(actionType));
            newAction.setConfigJSON(config);
            newAction.setCondition(condition);
            newAction.setCustomData(Sets.newHashSet(customData));

            resultAction = actionService.save(newAction);

            subscriberService.addActionToSubscriber(subscriberService.transformId(subscriberId), eventType,
                resultAction);
        } else {
            ActionEntity exists = actionService.findById(Long.valueOf(id))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Action with id <%s> not found", id)));
            exists.setType(NotificationType.valueOf(actionType));
            exists.setConfigJSON(config);
            exists.setCondition(condition);
            exists.setCustomData(Sets.newHashSet(customData));

            actionService.save(exists);

            resultAction = exists;
        }

        return resultAction;
    }

    private void throwParamIsMandatory(String param) {
        throw new IllegalArgumentException(String.format("Param <%s> is mandatory", param));
    }

    @NotNull
    @Override
    public List<DelStatus> delete(@NotNull List<String> recordIds) throws Exception {
        List<DelStatus> result = new ArrayList<>();
        for (String recordId : recordIds) {
            try {
                actionService.deleteById(Long.valueOf(recordId));
                result.add(DelStatus.OK);
            } catch (EmptyResultDataAccessException e) {
                log.error(String.format("Subscription deletion error. Id: '%s'", recordId), e);
                result.add(DelStatus.ERROR);
            }
        }
        return result;
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) {
        return getValue(recordId);
    }

    private ActionDto getValue(String recordId) {

        ActionEntity entity = Optional.of(recordId)
            .filter(str -> !str.isEmpty())
            .map(x -> actionService.findById(Long.valueOf(x))
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Subscriber with id <%s> not found!", recordId))))
            .orElseGet(ActionEntity::new);

        return factory.fromAction(entity);
    }

    @NotNull
    @Override
    public String getId() {
        return ID;
    }
}
