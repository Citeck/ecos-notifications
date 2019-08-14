package ru.citeck.ecos.notifications.domain.subscribe;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
public class Subscription extends BaseEntity {

    private String eventType;

    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "subscribe")
    private Set<Action> actions = new HashSet<>();

}
