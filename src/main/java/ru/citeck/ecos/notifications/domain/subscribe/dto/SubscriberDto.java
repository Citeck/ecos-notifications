package ru.citeck.ecos.notifications.domain.subscribe.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class SubscriberDto {

    private String id;
    private List<SubscriptionDto> subscribes = new ArrayList<>();

}
