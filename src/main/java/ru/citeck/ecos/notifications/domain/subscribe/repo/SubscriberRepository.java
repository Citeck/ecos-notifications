package ru.citeck.ecos.notifications.domain.subscribe.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * @author Roman Makarskiy
 */
public interface SubscriberRepository extends CrudRepository<SubscriberEntity, SubscriberId> {

    @Query("select distinct tenantId from SubscriberEntity")
    List<String> findDistinctTenants();

}
