package ru.citeck.ecos.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.domain.subscribe.Subscriber;
import ru.citeck.ecos.notifications.domain.subscribe.SubscriberId;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDTO;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDtoFactory;
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
public class SubscriberRecords extends CrudRecordsDAO<SubscriberDTO> {

    private static final String ADD_SUBSCRIBE_ACTION = "add-subscribe-action";
    private static final String UPDATE_SUBSCRIBE_ACTION = "update-subscribe-action";
    private static final String DELETE_SUBSCRIBE_ACTION = "delete-subscribe-action";

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
    public List<SubscriberDTO> getValuesToMutate(List<RecordRef> records) {
        return getValues(records);
    }

    @Override
    public List<SubscriberDTO> getMetaValues(List<RecordRef> records) {
        return getValues(records);
    }

    private List<SubscriberDTO> getValues(List<RecordRef> records) {
        return records.stream()
            .map(RecordRef::getId)
            .map(id ->
                Optional.of(id)
                    .filter(str -> !str.isEmpty())
                    .map(x -> subscriberService.getById(x)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Subscriber with id <%s> not found!", id))))
                    .orElseGet(Subscriber::new))
            .map(factory::fromSubscriber)
            .collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult mutate(RecordsMutation mutation) {
        RecordsMutResult result = new RecordsMutResult();

        for (RecordMeta meta : mutation.getRecords()) {
            if (isAction(meta, ADD_SUBSCRIBE_ACTION)) {
                String id = meta.getId().getId();
                if (StringUtils.isBlank(id)) {
                    throw new IllegalArgumentException("Subscriber id is mandatory parameter");
                }

                JsonNode subscribe = meta.getAttribute(ADD_SUBSCRIBE_ACTION);
                if (!subscribe.hasNonNull("event")) {
                    throw new IllegalArgumentException("Event is mandatory parameter");
                }

                if (!subscribe.hasNonNull("action")) {
                    throw new IllegalArgumentException("Action is mandatory parameter");
                }

                JsonNode actionNode = subscribe.get("action");
                if (!actionNode.hasNonNull("type")) {
                    throw new IllegalArgumentException("Action - type is mandatory parameter");
                }

                String actionType = actionNode.get("type").asText();
                JsonNode config = actionNode.get("config");

                String configStr = "";
                if (config != null && !config.isNull() && !config.isMissingNode()) {
                    configStr = config.toString();
                }


                Action action = new Action();
                action.setType(Action.Type.valueOf(actionType));
                action.setConfigJSON(configStr);

                JsonNode condition = actionNode.get("condition");
                if (condition != null && !condition.isNull() && !condition.isMissingNode()) {
                    action.setCondition(condition.asText());
                }

                SubscriberId subscriberId = subscriberService.transformId(id);

                String event = subscribe.get("event").asText();
                subscriberService.addSubscribeAction(subscriberId, event, action);

                result.addRecord(new RecordMeta(id));
                continue;
            }

            if (isAction(meta, UPDATE_SUBSCRIBE_ACTION)) {
                String subscriberId = meta.getId().getId();
                if (StringUtils.isBlank(subscriberId)) {
                    throw new IllegalArgumentException("Subscriber id is mandatory parameter");
                }

                JsonNode subscribe = meta.getAttribute(UPDATE_SUBSCRIBE_ACTION);
                if (!subscribe.hasNonNull("action")) {
                    throw new IllegalArgumentException("Action is mandatory parameter");
                }

                JsonNode actionNode = subscribe.get("action");
                if (!actionNode.hasNonNull("type")) {
                    throw new IllegalArgumentException("Action - type is mandatory parameter");
                }

                if (!actionNode.hasNonNull("id")) {
                    throw new IllegalArgumentException("Action - id is mandatory parameter");
                }

                Long id = actionNode.get("id").asLong();
                String actionType = actionNode.get("type").asText();
                JsonNode config = actionNode.get("config");

                String configStr = "";
                if (config != null && !config.isNull()) {
                    configStr = config.toString();
                }

                Action action = new Action();
                action.setType(Action.Type.valueOf(actionType));
                action.setConfigJSON(configStr);

                JsonNode condition = actionNode.get("condition");
                if (condition != null && !condition.isNull() && !condition.isMissingNode()) {
                    action.setCondition(condition.asText());
                }

                subscriberService.updateSubscribeAction(id, action);
                result.addRecord(new RecordMeta(subscriberId));
                continue;
            }

            if (isAction(meta, DELETE_SUBSCRIBE_ACTION)) {
                String subscriberId = meta.getId().getId();
                if (StringUtils.isBlank(subscriberId)) {
                    throw new IllegalArgumentException("Subscriber id is mandatory parameter");
                }

                JsonNode subscribe = meta.getAttribute(DELETE_SUBSCRIBE_ACTION);
                if (!subscribe.hasNonNull("action")) {
                    throw new IllegalArgumentException("Action is mandatory parameter");
                }

                JsonNode actionNode = subscribe.get("action");
                if (!actionNode.hasNonNull("id")) {
                    throw new IllegalArgumentException("Action - id is mandatory parameter");
                }

                Long id = actionNode.get("id").asLong();
                subscriberService.deleteSubscribeAction(id);
                result.addRecord(new RecordMeta(subscriberId));
            }

        }

        return result;
    }


    private boolean isAction(RecordMeta meta, String action) {
        JsonNode actionNode = meta.getAttribute(action);
        return actionNode != null && !actionNode.isMissingNode() && !actionNode.isNull();
    }

    @Override
    public RecordsMutResult save(List<SubscriberDTO> list) {
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
    public RecordsQueryResult<SubscriberDTO> getMetaValues(RecordsQuery recordsQuery) {
        return null;
    }
}
