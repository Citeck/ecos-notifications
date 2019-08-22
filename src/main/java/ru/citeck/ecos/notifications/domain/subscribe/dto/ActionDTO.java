package ru.citeck.ecos.notifications.domain.subscribe.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.Data;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.domain.subscribe.CustomData;

import java.util.HashMap;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class ActionDTO {

    private Long id;
    private Action.Type type;
    private String condition;
    private JsonNode config = NullNode.getInstance();
    private List<CustomData> customData;

}
