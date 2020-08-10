package ru.citeck.ecos.notifications.domain.subscribe.repo;

import lombok.Data;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Roman Makarskiy
 */
@Data
@Entity
@IdClass(SubscriberId.class)
@Table(name = "subscribers")
public class SubscriberEntity {
    @Id
    private String username;

    @Id
    private String tenantId;

    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name = "tenantId"),
        @JoinColumn(name = "username")
    })
    private Set<SubscriptionEntity> subscriptions = new HashSet<>();
}
