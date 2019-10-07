package ru.citeck.ecos.notifications.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events.EventConnection;
import ru.citeck.ecos.events.data.dto.task.TaskEventDTO;
import ru.citeck.ecos.events.data.dto.task.TaskEventType;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.domain.subscribe.Subscription;
import ru.citeck.ecos.notifications.repository.SubscriptionRepository;
import ru.citeck.ecos.notifications.service.processors.ActionProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Component
public class EventNotificationHandlersRegistrar extends AbstractEventHandlersRegistrar {

    private static final String QUEUE_NOTIFICATION_NAME = "notification";
    private static final String RECEIVE_ALL_KEY = "#";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EventConnection eventConnection;
    private final SubscriptionRepository subscriptionRepository;
    private final Map<String, List<ActionProcessor>> actionProcessors;

    public EventNotificationHandlersRegistrar(Set<String> tenantsRegistrar,
                                              @Autowired(required = false) EventConnection eventConnection,
                                              SubscriptionRepository subscriptionRepository,
                                              @Qualifier("actionProcessorRegistry")
                                                  Map<String, List<ActionProcessor>> actionProcessors) {
        super(tenantsRegistrar);
        this.eventConnection = eventConnection;
        this.subscriptionRepository = subscriptionRepository;
        this.actionProcessors = actionProcessors;
    }

    @Override
    public void registerImpl(String tenantId) {
        try {
            eventConnection.receive(RECEIVE_ALL_KEY, QUEUE_NOTIFICATION_NAME, tenantId,
                (consumerTag, message, channel) -> {
                    try {
                        String routingKey = message.getEnvelope().getRoutingKey();

                        //TODO: remove hard coded task processing, we need a some universal logic to find subscriptions
                        if (routingKey.startsWith("task.")) {
                            String msg = new String(message.getBody(), StandardCharsets.UTF_8);

                            TaskEventDTO dto = OBJECT_MAPPER.readValue(msg, TaskEventDTO.class);

                            Set<String> userSubscribers = getSubscribersUsers(dto);

                            log.debug("Found user subscribers:\n" + userSubscribers);

                            if (!userSubscribers.isEmpty()) {
                                String requiredEventType = getEventTypeByRoutingKey(routingKey);

                                List<Subscription> usersSubscriptions = subscriptionRepository.findUsersSubscribes(
                                    tenantId, new ArrayList<>(userSubscribers), requiredEventType);
                                usersSubscriptions.forEach(subscription -> {
                                    Set<Action> actions = subscription.getActions();

                                    actions.forEach(action -> {
                                        Action.Type type = action.getType();

                                        List<ActionProcessor> processors = actionProcessors.get(type.toString());
                                        if (CollectionUtils.isNotEmpty(processors)) {
                                            processors.forEach(actionProcessor -> actionProcessor.process(message,
                                                dto, action));
                                        }

                                    });
                                });
                            }
                        }
                    } catch (Throwable e) {
                        log.error("Failed process event", e);
                        channel.basicNack(message.getEnvelope().getDeliveryTag(), false, false);
                    }
                    channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                });
        } catch (IOException e) {
            throw new RuntimeException("Failed register receive", e);
        }
    }

    private Set<String> getSubscribersUsers(TaskEventDTO dto) {
        Set<String> users = new HashSet<>();

        switch (TaskEventType.resolve(dto.getType())) {
            case CREATE:
                users.addAll(dto.getTaskPooledUsers());
                break;
            case ASSIGN:
                users.add(dto.getAssignee());
                break;
            default:
                users.addAll(dto.getTaskPooledUsers());
                users.add(dto.getAssignee());
        }

        return users.stream()
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    private String getEventTypeByRoutingKey(String routingKey) {
        return StringUtils.equalsAny(routingKey, TaskEventType.ASSIGN.toString(),
            TaskEventType.CREATE.toString()) ? TaskEventType.ASSIGN.toString() : routingKey;
    }

}
