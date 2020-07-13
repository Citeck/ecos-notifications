package ru.citeck.ecos.notifications.domain.template.entity;

import lombok.Data;
import lombok.Getter;
import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Data
@Entity
@Table(name = "notification_template")
public class NotificationTemplate extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter
    private Long id;

    @Column(name = "ext_id")
    private String extId;

    private String name;

    @Column(name = "notification_title")
    private String notificationTitle;

    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "template")
    @MapKey(name = "lang")
    private Map<String, TemplateData> data = new HashMap<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationTemplate that = (NotificationTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }

}
