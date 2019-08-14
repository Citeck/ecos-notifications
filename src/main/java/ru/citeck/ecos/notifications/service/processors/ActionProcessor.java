package ru.citeck.ecos.notifications.service.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.events.data.dto.EventDTO;
import ru.citeck.ecos.notifications.domain.subscribe.Action;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.Map;

/**
 * @author Roman Makarskiy
 */
public abstract class ActionProcessor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Getter
    @Setter
    protected String id;

    protected abstract void processImpl(Delivery message, EventDTO dto, Action action);

    public void process(Delivery message, @NonNull EventDTO dto, @NonNull Action action) {
        if (processRequired(action, dto)) {
            processImpl(message, dto, action);
        }
    }

    private boolean processRequired(Action action, EventDTO dto) {
        String conditionScript = action.getCondition();
        if (StringUtils.isBlank(conditionScript)) {
            return true;
        }

        JsonNode jsonNode = OBJECT_MAPPER.valueToTree(dto);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = OBJECT_MAPPER.convertValue(jsonNode, Map.class);

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine scriptEngine = manager.getEngineByName("groovy");
        Object result;
        try {
            result = scriptEngine.eval(conditionScript,
                new SimpleBindings(map));
        } catch (ScriptException e) {
            throw new RuntimeException("Failed eval groove script:\n" + conditionScript, e);
        }

        return Boolean.parseBoolean(result.toString());
    }

}
