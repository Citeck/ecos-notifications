package ru.citeck.ecos.notifications.domain.subscribe.repo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.citeck.ecos.notifications.domain.BaseEntity;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Roman Makarskiy
 */
@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "subscriptions")
public class SubscriptionEntity extends BaseEntity {

    private String eventType;

    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "subscribe")
    private Set<ActionEntity> actions = new HashSet<>();

}
