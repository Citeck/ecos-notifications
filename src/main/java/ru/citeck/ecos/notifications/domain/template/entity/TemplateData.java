package ru.citeck.ecos.notifications.domain.template.entity;

import lombok.Data;
import lombok.Getter;
import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

@Data
@Entity
@Table(name = "template_data")
public class TemplateData extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter
    private Long id;

    @NotNull
    private String name;

    @NotNull
    @Size(min = 2)
    private String lang;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private NotificationTemplate template;

    private byte[] data;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateData that = (TemplateData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }

}
