package ru.citeck.ecos.notifications.domain.subscribe;

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
public class Subscriber {
    @Id
    private String username;

    @Id
    private String tenantId;

    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name = "tenantId"),
        @JoinColumn(name = "username")
    })
    private Set<Subscription> subscriptions = new HashSet<>();
}
