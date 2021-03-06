package ru.citeck.ecos.notifications.domain.subscribe.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @author Roman Makarskiy
 */
public interface SubscriptionRepository extends CrudRepository<SubscriptionEntity, Long> {

    @Query(nativeQuery = true, value =
        "SELECT * FROM subscriptions WHERE tenant_id = :tenantId AND LOWER(username) IN :users AND event_type = :eventType")
    List<SubscriptionEntity> findUsersSubscribes(@Param("tenantId") String tenantId,
                                                 @Param("users") List<String> usersLowerCase,
                                                 @Param("eventType") String eventType);

}
