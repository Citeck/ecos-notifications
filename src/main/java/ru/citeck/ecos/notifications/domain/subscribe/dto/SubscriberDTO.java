package ru.citeck.ecos.notifications.domain.subscribe.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class SubscriberDTO {

    private String id;
    private List<SubscriptionDTO> subscribes = new ArrayList<>();

}
