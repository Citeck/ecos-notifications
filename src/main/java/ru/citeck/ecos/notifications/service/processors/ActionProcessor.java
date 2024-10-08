package ru.citeck.ecos.notifications.service.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.events.data.dto.EventDto;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.CustomDataEntity;
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    private static final String WORKSPACE_SPACES_STORE = "workspace://SpacesStore/";

    private RecordsService recordsService;
    private FreemarkerTemplateEngineService templateEngineService;

    @Getter
    @Setter
    protected String id;

    protected abstract void processImpl(Delivery message, EventDto dto, ActionEntity action, Map<String, Object> model);

    public void process(Delivery message, @NonNull EventDto dto, @NonNull ActionEntity action) {
        log.debug(String.format("============ Start process actions id: %s =============", action.getId()));
        log.debug("Action: \n" + action);

        Map<String, Object> model = new HashMap<>();

        JsonNode dtoData = dto.getData();

        model.put(MODEL_EVENT, OBJECT_MAPPER.convertValue(dtoData, Map.class));
        model.put(MODEL_CUSTOM_DATA, getProcessedCustomData(action, dtoData));

        log.debug(String.format("Prepared model:\n%s", model));

        if (processRequired(action, model)) {
            processImpl(message, dto, action, model);
        }

        log.debug(String.format("============= End process actions id: %s ==============", action.getId()));
    }

    private Map<String, Object> getProcessedCustomData(ActionEntity action, JsonNode dtoData) {
        Map<String, Object> result = new HashMap<>();
        if (CollectionUtils.isEmpty(action.getCustomData())) {
            return result;
        }

        CustomDataEntity[] customData = getTemplatedData(action.getCustomData(), dtoData);

        for (CustomDataEntity data : customData) {
            EntityRef recordRef = resolveRecordRef(data.getRecord());

            RecordAtts attributes = AuthContext.runAsSystem(() ->
                recordsService.getAtts(recordRef, data.getAttributes())
            );
            result.put(data.getVariable(), attributes);
        }

        return result;
    }

    private EntityRef resolveRecordRef(String id) {
        if (StringUtils.isBlank(id)) {
            return EntityRef.EMPTY;
        }

        EntityRef ref = EntityRef.valueOf(id);
        if (StringUtils.isBlank(ref.getAppName()) && id.startsWith(WORKSPACE_SPACES_STORE)) {
            return EntityRef.create("alfresco", "", id);
        }

        return ref;
    }

    private CustomDataEntity[] getTemplatedData(Set<CustomDataEntity> customData, JsonNode dtoData) {
        String customDataToProcess;
        try {
            customDataToProcess = OBJECT_MAPPER.writeValueAsString(customData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed write custom data as string", e);
        }

        HashMap<String, Object> model = new HashMap<>();
        model.put(MODEL_EVENT, dtoData);

        String processedCustomData = templateEngineService.process(CUSTOM_DATA_TEMPLATE_KEY, customDataToProcess, model);

        try {
            return OBJECT_MAPPER.readValue(processedCustomData, CustomDataEntity[].class);
        } catch (IOException e) {
            throw new RuntimeException("Failed read custom data", e);
        }
    }

    private boolean processRequired(ActionEntity action, Map<String, Object> model) {
        String conditionScript = action.getCondition();
        if (StringUtils.isBlank(conditionScript)) {
            return true;
        }

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine scriptEngine = manager.getEngineByName(GROOVY_ENGINE);
        Object result = false;
        try {
            result = scriptEngine.eval(conditionScript,
                new SimpleBindings(model));
        } catch (ScriptException e) {
            log.error("Failed eval groove script:\n" + conditionScript + "\n action:\n" + action, e);
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
