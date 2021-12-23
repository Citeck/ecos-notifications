package ru.citeck.ecos.notifications.domain.subscribe.repo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.citeck.ecos.notifications.domain.BaseEntity;
import ru.citeck.ecos.notifications.lib.NotificationType;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Roman Makarskiy
 */
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "actions")
public class ActionEntity extends BaseEntity {

    @Lob
    private String configJSON;

    @Lob
    private String condition;

    @JoinColumn(name = "action")
    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<CustomDataEntity> customData = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    public String getConfigJSON() {
        return configJSON;
    }

    public void setConfigJSON(String configJSON) {
        this.configJSON = configJSON;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public Set<CustomDataEntity> getCustomData() {
        return customData;
    }

    public void setCustomData(Set<CustomDataEntity> customData) {
        this.customData = customData;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "ActionEntity{" +
            "id=" + id +
            ", configJSON='" + configJSON + '\'' +
            ", condition='" + condition + '\'' +
            ", customData=" + customData +
            ", type=" + type +
            '}';
    }
}
