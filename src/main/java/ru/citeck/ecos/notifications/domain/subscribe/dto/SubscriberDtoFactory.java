package ru.citeck.ecos.notifications.domain.subscribe.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.domain.subscribe.Subscription;
import ru.citeck.ecos.notifications.domain.subscribe.Subscriber;
import ru.citeck.ecos.notifications.domain.subscribe.SubscriberId;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class SubscriberDtoFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public SubscriberDTO fromSubscriber(Subscriber subscriber) {
        SubscriberDTO dto = new SubscriberDTO();

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

    public SubscriptionDTO fromSubscribe(Subscription subscription) {
        SubscriptionDTO dto = new SubscriptionDTO();
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

    public ActionDTO fromAction(Action action) {
        ActionDTO dto = new ActionDTO();
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

        return dto;
    }

}
