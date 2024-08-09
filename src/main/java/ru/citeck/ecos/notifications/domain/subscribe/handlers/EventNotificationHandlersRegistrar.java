package ru.citeck.ecos.notifications.domain.subscribe.handlers;

import com.rabbitmq.client.Delivery;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events.EventConnection;
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory;
import ru.citeck.ecos.events.data.dto.task.TaskEventDto;
import ru.citeck.ecos.events.data.dto.task.TaskEventType;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriptionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.SubscriptionRepository;
import ru.citeck.ecos.notifications.lib.NotificationType;
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
        if (eventConnection == null) {
            log.warn("Receive not registered, cause eventConnection is nit found");
            return;
        }

        try {
            eventConnection.receive(RECEIVE_ALL_KEY, QUEUE_NOTIFICATION_NAME, tenantId,
                (consumerTag, message, channel) -> {

                    StopWatch stopWatch = new StopWatch();
                    StringBuilder debugMsg = new StringBuilder();

                    try {
                        String routingKey = message.getEnvelope().getRoutingKey();

                        if (log.isDebugEnabled()) {
                            stopWatch.start();

                            debugMsg.append("\n======= RECEIVE NOTIFICATION EVENT =======");
                            debugMsg.append("\nroutingKey: ").append(routingKey);
                        }

                        //TODO: remove hard coded task processing, we need a some universal logic to find subscriptions
                        if (routingKey.startsWith("task.")) {

                            String msg = new String(message.getBody(), StandardCharsets.UTF_8);
                            TaskEventDto dto = EventDtoFactory.fromEventDtoMsg(msg);

                            log.debug("Receive taskEventDto: \n{}", dto);

                            Set<String> userSubscribers = getSubscribersUsers(dto);

                            log.debug("Found user subscribers:\n{}", userSubscribers);

                            if (userSubscribers.isEmpty()) {
                                return;
                            }

                            String requiredEventType = getEventTypeByRoutingKey(routingKey);

                            List<SubscriptionEntity> usersSubscriptions = subscriptionRepository.findUsersSubscribes(
                                tenantId, new ArrayList<>(userSubscribers), requiredEventType);

                            usersSubscriptions.forEach(subscription -> {
                                Set<ActionEntity> actions = subscription.getActions();

                                actions.forEach(action -> {
                                    NotificationType type = action.getType();

                                    List<ActionProcessor> processors = actionProcessors.getOrDefault(type.toString(),
                                        Collections.emptyList());

                                    processors.forEach(actionProcessor ->
                                        safelyHandleAction(actionProcessor, action, message, dto)
                                    );

                                });
                            });
                        }
                    } catch (Throwable e) {
                        log.error("Failed process event message..." +
                                "\ntenantId: {}" +
                                "\nconsumerTag: {}" +
                                "\nmessage: {}", tenantId, consumerTag,
                            new String(message.getBody(), StandardCharsets.UTF_8), e);
                    } finally {
                        channel.basicAck(message.getEnvelope().getDeliveryTag(), false);

                        if (log.isDebugEnabled()) {
                            stopWatch.stop();

                            debugMsg.append("\nTime: ").append(DurationFormatUtils.formatDurationHMS(
                                stopWatch.getTime()));
                            debugMsg.append("\n===== RECEIVE NOTIFICATION EVENT END =====");

                            log.debug(debugMsg.toString());
                        }
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException("Failed register receive", e);
        }
    }

    private void safelyHandleAction(ActionProcessor actionProcessor, ActionEntity action, Delivery message,
                                    TaskEventDto eventDto) {
        try {
            actionProcessor.process(message, EventDtoFactory.toEventDto(eventDto), action);
        } catch (Exception e) {
            log.error("Failed process event notification on action processor: " + actionProcessor.getId() +
                ". Action id: " + action.getId(), e);
        }
    }

    private Set<String> getSubscribersUsers(TaskEventDto dto) {
        Set<String> users = new HashSet<>();

        switch (TaskEventType.resolve(dto.getType())) {
            case CREATE:
                users.addAll(dto.getTaskPooledUsers());
                break;
            case ASSIGN:
                if (isExplicitAssignmentEvent(dto)) {
                    users.add(dto.getAssignee());
                }
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

    private boolean isExplicitAssignmentEvent(TaskEventDto dto) {
        return CollectionUtils.isEmpty(dto.getTaskPooledUsers()) && CollectionUtils.isEmpty(dto.getTaskPooledActors());
    }

    private String getEventTypeByRoutingKey(String routingKey) {
        return StringUtils.equalsAny(routingKey, TaskEventType.ASSIGN.toString(),
            TaskEventType.CREATE.toString()) ? TaskEventType.ASSIGN.toString() : routingKey;
    }

}
