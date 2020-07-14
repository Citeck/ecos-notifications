package ru.citeck.ecos.notifications.domain.subscribe.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.citeck.ecos.notifications.domain.BaseEntity;
import ru.citeck.ecos.notifications.domain.notification.NotificationType;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "actions")
public class Action extends BaseEntity {

    @Lob
    private String configJSON;

    @Lob
    private String condition;

    @JoinColumn(name = "action")
    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<CustomData> customData = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private NotificationType type;

}
