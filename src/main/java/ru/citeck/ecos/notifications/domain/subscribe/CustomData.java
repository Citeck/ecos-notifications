package ru.citeck.ecos.notifications.domain.subscribe;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "custom_data")
public class CustomData extends BaseEntity {

    private String variable;
    private String record;

    @ElementCollection
    @MapKeyColumn(name = "attr")
    @Column(name="schema", length = 2048)
    @CollectionTable(name = "custom_data_attributes", joinColumns = @JoinColumn(name = "custom_data_id"))
    private Map<String, String> attributes = new HashMap<>();

}
