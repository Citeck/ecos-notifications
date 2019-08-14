package ru.citeck.ecos.notifications.domain.subscribe.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class SubscriptionDTO {

    private Long id;
    private String eventType;
    private List<ActionDTO> actions = new ArrayList<>();

}
