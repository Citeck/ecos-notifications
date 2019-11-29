package ru.citeck.ecos.notifications.config;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.citeck.ecos.events.EventConnection;
import ru.citeck.ecos.notifications.repository.SubscriberRepository;
import ru.citeck.ecos.notifications.service.handlers.AbstractEventHandlersRegistrar;
import ru.citeck.ecos.notifications.service.processors.ActionProcessor;

import java.util.*;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Configuration
public class EventConfig {

    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.username}")
    private String rabbitUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    @Bean
    @Profile("!test")
    public EventConnection eventConnection() {
        return new EventConnection.Builder()
            .host(rabbitHost)
            .username(rabbitUsername)
            .password(rabbitPassword)
            .build();
    }

    @Bean(name = "tenantRegistrar")
    public Set<String> tenantsRegistrar() {
        return Sets.newConcurrentHashSet();
    }

    @Bean(name = "actionProcessorRegistry")
    public Map<String, List<ActionProcessor>> actionProcessorRegistry(ApplicationContext applicationContext) {
        Map<String, List<ActionProcessor>> mappingRegistry = new HashMap<>();

        Map<String, ActionProcessor> processors = applicationContext.getBeansOfType(ActionProcessor.class);
        processors.forEach((s, actionProcessor) -> {
            String id = actionProcessor.getId();
            mappingRegistry.computeIfAbsent(id, k -> new ArrayList<>()).add(actionProcessor);
            log.info("Register action processor with id: " + id);
        });

        return mappingRegistry;
    }

    @Bean
    public CommandLineRunner registerExistsTenants(SubscriberRepository subscriberRepository,
                                                   ApplicationContext applicationContext) {
        return args -> {
            Map<String, AbstractEventHandlersRegistrar> registrars = applicationContext.getBeansOfType(
                AbstractEventHandlersRegistrar.class);
            List<String> subscribers = subscriberRepository.findDistinctTenants();

            registrars.forEach((s, handler) -> subscribers.forEach(handler::register));
        };
    }

}
