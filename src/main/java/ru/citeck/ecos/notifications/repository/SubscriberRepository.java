package ru.citeck.ecos.notifications.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import ru.citeck.ecos.notifications.domain.subscribe.Subscriber;
import ru.citeck.ecos.notifications.domain.subscribe.SubscriberId;

import java.util.List;

/**
 * @author Roman Makarskiy
 */
public interface SubscriberRepository extends CrudRepository<Subscriber, SubscriberId> {

    @Query("select distinct tenantId from Subscriber")
    List<String> findDistinctTenants();

}
