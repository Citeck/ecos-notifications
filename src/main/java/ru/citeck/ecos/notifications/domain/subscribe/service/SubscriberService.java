package ru.citeck.ecos.notifications.domain.subscribe.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.notifications.domain.subscribe.handlers.AbstractEventHandlersRegistrar;
import ru.citeck.ecos.notifications.domain.subscribe.repo.*;

import java.util.*;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Service
@Transactional
public class SubscriberService implements ApplicationContextAware {

    private final SubscriberRepository subscriberRepository;
    private ApplicationContext applicationContext;

    public SubscriberService(SubscriberRepository subscriberRepository) {
        this.subscriberRepository = subscriberRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SubscriberEntity> getById(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }

        return subscriberRepository.findById(transformId(id));
    }

    public SubscriberId transformId(String id) {
        String[] splitted = StringUtils.split(id, SubscriberId.SEPARATOR);
        if (splitted.length != 2) {
            throw new IllegalArgumentException("Incorrect format of Subscriber id. Id: " + id);
        }

        String tenantId = splitted[0];
        String username = splitted[1];

        SubscriberId subscriberId = new SubscriberId();
        subscriberId.setTenantId(tenantId);
        subscriberId.setUsername(username);

        return subscriberId;
    }

    @Transactional(readOnly = true)
    public Optional<SubscriberEntity> getById(SubscriberId id) {
        return getById(id.toString());
    }

    public void addActionToSubscriber(SubscriberId id, String event, ActionEntity action) {
        Optional<SubscriberEntity> optSubscriber = getById(id);

        SubscriberEntity subscriber;
        if (optSubscriber.isPresent()) {
            subscriber = optSubscriber.get();
        } else {
            subscriber = new SubscriberEntity();
            subscriber.setUsername(id.getUsername());
            subscriber.setTenantId(id.getTenantId());
        }

        Set<SubscriptionEntity> subscriptions = subscriber.getSubscriptions() != null ? subscriber.getSubscriptions()
            : new HashSet<>();
        SubscriptionEntity foundSubscription = subscriptions.stream()
            .filter(work -> Objects.equals(work.getEventType(), event))
            .findFirst()
            .orElseGet(() -> {
                SubscriptionEntity w = new SubscriptionEntity();
                w.setEventType(event);
                return w;
            });

        Set<ActionEntity> actions = foundSubscription.getActions() != null ? foundSubscription.getActions() : new HashSet<>();
        actions.add(action);

        foundSubscription.setActions(actions);
        subscriptions.add(foundSubscription);

        subscriber.setSubscriptions(subscriptions);

        subscriberRepository.save(subscriber);

        registerNewSubscriber(subscriber.getTenantId());
    }

    private void registerNewSubscriber(String tenantId) {
        Map<String, AbstractEventHandlersRegistrar> beansOfType = applicationContext.getBeansOfType(
            AbstractEventHandlersRegistrar.class);
        beansOfType.forEach((s, eventRegistrar) -> eventRegistrar.register(tenantId));
    }

    public void deleteSubscriber(SubscriberId id) {
        subscriberRepository.deleteById(id);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
