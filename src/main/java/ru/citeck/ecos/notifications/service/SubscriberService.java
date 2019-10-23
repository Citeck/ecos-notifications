package ru.citeck.ecos.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.domain.subscribe.Subscriber;
import ru.citeck.ecos.notifications.domain.subscribe.SubscriberId;
import ru.citeck.ecos.notifications.domain.subscribe.Subscription;
import ru.citeck.ecos.notifications.repository.SubscriberRepository;
import ru.citeck.ecos.notifications.service.handlers.AbstractEventHandlersRegistrar;

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
    public Optional<Subscriber> getById(String id) {
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
    public Optional<Subscriber> getById(SubscriberId id) {
        return getById(id.toString());
    }

    public void addActionToSubscriber(SubscriberId id, String event, Action action) {
        Optional<Subscriber> optSubscriber = getById(id);

        Subscriber subscriber;
        if (optSubscriber.isPresent()) {
            subscriber = optSubscriber.get();
        } else {
            subscriber = new Subscriber();
            subscriber.setUsername(id.getUsername());
            subscriber.setTenantId(id.getTenantId());
        }

        Set<Subscription> subscriptions = subscriber.getSubscriptions() != null ? subscriber.getSubscriptions()
            : new HashSet<>();
        Subscription foundSubscription = subscriptions.stream()
            .filter(work -> Objects.equals(work.getEventType(), event))
            .findFirst()
            .orElseGet(() -> {
                Subscription w = new Subscription();
                w.setEventType(event);
                return w;
            });

        Set<Action> actions = foundSubscription.getActions() != null ? foundSubscription.getActions() : new HashSet<>();
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
