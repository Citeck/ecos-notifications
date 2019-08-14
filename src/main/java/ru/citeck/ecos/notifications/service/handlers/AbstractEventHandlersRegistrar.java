package ru.citeck.ecos.notifications.service.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Set;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public abstract class AbstractEventHandlersRegistrar {

    private Set<String> tenantsRegistrar;

    public AbstractEventHandlersRegistrar(@Qualifier("tenantRegistrar") Set<String> tenantsRegistrar) {
        this.tenantsRegistrar = tenantsRegistrar;
    }

    protected abstract void registerImpl(String tenantId);

    public void register(String tenantId) {
        if (tenantsRegistrar.contains(tenantId)) {
            return;
        }

        registerImpl(tenantId);
        tenantsRegistrar.add(tenantId);

        log.info("Register tenant: " + tenantId);
    }

}
