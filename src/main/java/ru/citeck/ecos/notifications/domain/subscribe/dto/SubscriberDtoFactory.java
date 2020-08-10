package ru.citeck.ecos.notifications.domain.subscribe.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriberEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriberId;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriptionEntity;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class SubscriberDtoFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public SubscriberDto fromSubscriber(SubscriberEntity subscriber) {
        SubscriberDto dto = new SubscriberDto();

        SubscriberId id = new SubscriberId();
        id.setUsername(subscriber.getUsername());
        id.setTenantId(subscriber.getTenantId());

        dto.setId(id.toString());
        dto.setSubscribes(
            subscriber.getSubscriptions()
                .stream()
                .map(this::fromSubscribe)
                .collect(Collectors.toList())
        );

        return dto;
    }

    public SubscriptionDto fromSubscribe(SubscriptionEntity subscription) {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setId(subscription.getId());
        dto.setEventType(subscription.getEventType());
        dto.setActions(
            subscription.getActions()
                .stream()
                .map(this::fromAction)
                .collect(Collectors.toList())
        );

        return dto;
    }

    public ActionDto fromAction(ActionEntity action) {
        ActionDto dto = new ActionDto();
        dto.setId(action.getId());
        dto.setType(action.getType());
        dto.setCondition(action.getCondition());

        String configJSON = action.getConfigJSON();
        if (StringUtils.isNotBlank(configJSON)) {
            JsonNode jsonNode;

            try {
                jsonNode = OBJECT_MAPPER.readValue(configJSON, JsonNode.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed read JSON from action", e);
            }

            dto.setConfig(jsonNode);
        }

        dto.setCustomData(action.getCustomData());

        return dto;
    }

}
