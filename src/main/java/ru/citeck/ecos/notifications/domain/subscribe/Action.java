package ru.citeck.ecos.notifications.domain.subscribe;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;

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

    @Enumerated(EnumType.STRING)
    private Type type;

    public static enum Type {
        FIREBASE_NOTIFICATION, EMAIL_NOTIFICATION
    }

}
