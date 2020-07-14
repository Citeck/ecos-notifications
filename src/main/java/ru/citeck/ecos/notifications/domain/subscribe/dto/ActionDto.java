package ru.citeck.ecos.notifications.domain.subscribe.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.Data;
import ru.citeck.ecos.notifications.domain.subscribe.entity.CustomData;
import ru.citeck.ecos.notifications.domain.notification.NotificationType;

import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class ActionDto {

    private Long id;
    private NotificationType type;
    private String condition;
    private JsonNode config = NullNode.getInstance();
    private List<CustomData> customData;

}
