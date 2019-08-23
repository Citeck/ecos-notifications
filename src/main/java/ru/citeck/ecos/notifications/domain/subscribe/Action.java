package ru.citeck.ecos.notifications.domain.subscribe;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    @Column(columnDefinition = "TEXT")
    private String configJSON;

    @Column(columnDefinition = "TEXT")
    private String condition;

    @JoinColumn(name = "action")
    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<CustomData> customData = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Type type;

    public enum Type {
        FIREBASE_NOTIFICATION, EMAIL_NOTIFICATION
    }

}
