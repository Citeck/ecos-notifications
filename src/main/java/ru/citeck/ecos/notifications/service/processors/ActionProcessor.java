package ru.citeck.ecos.notifications.service.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.events.data.dto.EventDTO;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.domain.subscribe.CustomData;
import ru.citeck.ecos.notifications.service.FreemarkerTemplateEngineService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public abstract class ActionProcessor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL_EVENT = "event";
    private static final String MODEL_CUSTOM_DATA = "customData";
    private static final String GROOVY_ENGINE = "groovy";
    private static final String CUSTOM_DATA_TEMPLATE_KEY = "customDataTemplate";

    private RecordsService recordsService;
    private FreemarkerTemplateEngineService templateEngineService;

    @Getter
    @Setter
    protected String id;

    protected abstract void processImpl(Delivery message, EventDTO dto, Action action, Map<String, Object> model);

    public void process(Delivery message, @NonNull EventDTO dto, @NonNull Action action) {
        log.debug(String.format("============ Start process actions id: %s =============", action.getId()));
        log.debug("Action: \n" + action);

        Map<String, Object> model = new HashMap<>();

        model.put(MODEL_EVENT, OBJECT_MAPPER.convertValue(dto, Map.class));
        model.put(MODEL_CUSTOM_DATA, getProcessedCustomData(action, dto));

        log.debug(String.format("Prepared model:\n%s", model));

        if (processRequired(action, model)) {
            processImpl(message, dto, action, model);
        }

        log.debug(String.format("============= End process actions id: %s ==============", action.getId()));
    }

    private Map<String, Object> getProcessedCustomData(Action action, EventDTO dto) {
        Map<String, Object> result = new HashMap<>();
        if (CollectionUtils.isEmpty(action.getCustomData())) {
            return result;
        }

        CustomData[] customData = getTemplatedData(action.getCustomData(), dto);

        for (CustomData data : customData) {
            RecordRef recordRef = RecordRef.valueOf(data.getRecord());
            RecordMeta attributes = recordsService.getAttributes(recordRef, data.getAttributes());
            result.put(data.getVariable(), attributes);
        }

        return result;
    }

    private CustomData[] getTemplatedData(List<CustomData> customData, EventDTO dto) {
        String customDataToProcess;
        try {
            customDataToProcess = OBJECT_MAPPER.writeValueAsString(customData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed write custom data as string", e);
        }

        HashMap<String, Object> model = new HashMap<>();
        model.put(MODEL_EVENT, dto);

        String processedCustomData = templateEngineService.process(CUSTOM_DATA_TEMPLATE_KEY, customDataToProcess, model);

        try {
            return OBJECT_MAPPER.readValue(processedCustomData, CustomData[].class);
        } catch (IOException e) {
            throw new RuntimeException("Failed read custom data", e);
        }
    }

    private boolean processRequired(Action action, Map<String, Object> model) {
        String conditionScript = action.getCondition();
        if (StringUtils.isBlank(conditionScript)) {
            return true;
        }

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine scriptEngine = manager.getEngineByName(GROOVY_ENGINE);
        Object result;
        try {
            result = scriptEngine.eval(conditionScript,
                new SimpleBindings(model));
        } catch (ScriptException e) {
            throw new RuntimeException("Failed eval groove script:\n" + conditionScript, e);
        }

        log.debug(String.format("Evaluate groovy condition... \n" +
            "script:\n%s\n" +
            "result: <%s>", conditionScript, result));

        return Boolean.parseBoolean(result.toString());
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    public void setTemplateEngineService(FreemarkerTemplateEngineService templateEngineService) {
        this.templateEngineService = templateEngineService;
    }
}
