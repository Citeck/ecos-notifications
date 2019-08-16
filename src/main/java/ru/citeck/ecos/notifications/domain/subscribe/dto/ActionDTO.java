package ru.citeck.ecos.notifications.domain.subscribe.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.Data;
import ru.citeck.ecos.notifications.domain.subscribe.Action;

/**
 * @author Roman Makarskiy
 */
@Data
public class ActionDTO {

    private Long id;
    private Action.Type type;
    private JsonNode config = NullNode.getInstance();
    private String condition;

}
