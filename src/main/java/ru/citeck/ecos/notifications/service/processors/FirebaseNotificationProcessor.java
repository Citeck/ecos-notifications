package ru.citeck.ecos.notifications.service.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import com.rabbitmq.client.Delivery;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events.data.dto.EventDto;
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory;
import ru.citeck.ecos.events.data.dto.task.TaskEventDto;
import ru.citeck.ecos.events.data.dto.task.TaskEventType;
import ru.citeck.ecos.notifications.config.ApplicationProperties;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService;
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService;
import ru.citeck.ecos.notifications.service.TemplateService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Component
public class FirebaseNotificationProcessor extends ActionProcessor {

    private static final String ID = "FIREBASE_NOTIFICATION";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ERROR_CODE_TOKEN_NOT_REGISTERED = "registration-token-not-registered";

    private static final String PARAM_FIREBASE_CLIENT_REG_TOKEN = "fireBaseClientRegToken";
    private static final String PARAM_DEVICE_TYPE = "deviceType";
    private static final String TEMPLATE_ID = "templateId";
    private static final String TITLE_TEMPLATE = "titleTemplate";
    private static final String BODY_TEMPLATE = "bodyTemplate";
    private static final String DEVICE_ANDROID = "android";
    private static final String DEVICE_IOS = "ios";
    private static final String PARAM_TASK_ID = "taskId";
    private static final String PARAM_DOCUMENT = "documentRef";
    private static final String TITLE_TEMPLATE_KEY = "titleTemplate";
    private static final String BODY_TEMPLATE_KEY = "bodyTemplate";

    private final FreemarkerTemplateEngineService templateEngineService;
    private final TemplateService templateService;
    private final ApplicationProperties appProps;
    private final ActionService actionService;

    {
        setId(ID);
    }

    public FirebaseNotificationProcessor(FreemarkerTemplateEngineService templateEngineService,
                                         TemplateService templateService, ApplicationProperties appProps,
                                         ActionService actionService) {
        this.templateEngineService = templateEngineService;
        this.templateService = templateService;
        this.appProps = appProps;
        this.actionService = actionService;
    }

    @Override
    protected void processImpl(Delivery message, EventDto dto, ActionEntity action, Map<String, Object> model) {
        log.debug("Process DTO: \n" + dto);
        JsonNode config;
        try {
            config = OBJECT_MAPPER.readValue(action.getConfigJSON(), JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed read json from string", e);
        }

        if (!config.hasNonNull(PARAM_FIREBASE_CLIENT_REG_TOKEN)) {
            throw new IllegalStateException("Action config does not contains firebase registration token");
        }

        if (!config.hasNonNull(PARAM_DEVICE_TYPE)) {
            throw new IllegalStateException("Action config does not contains device type");
        }

        String eventType = dto.resolveType();
        Template template = getTemplate(eventType, config);

        String title = templateEngineService.process(TITLE_TEMPLATE_KEY, template.getTitle(), model);
        String body = templateEngineService.process(BODY_TEMPLATE_KEY, template.getBody(), model);

        String registrationToken = config.get(PARAM_FIREBASE_CLIENT_REG_TOKEN).asText();
        String deviceType = config.get(PARAM_DEVICE_TYPE).asText();

        Message fireBaseMessage;
        Map<String, String> customData = prepareCustomData(dto);

        switch (deviceType) {
            case DEVICE_ANDROID: {
                fireBaseMessage = buildAndroidMessage(title, body, registrationToken, customData);
                break;
            }
            case DEVICE_IOS: {
                fireBaseMessage = buildIosMessage(title, body, registrationToken, customData);
                break;
            }
            default: {
                throw new IllegalStateException("Unsupported device type");
            }
        }

        String response;
        try {
            log.debug(String.format("Trying send message to firebase...\ntitle: <%s>\nbody: <%s>\ncustomData: <%s>",
                title, body, customData));
            response = FirebaseMessaging.getInstance().send(fireBaseMessage);
            log.debug("Successfully sent message: " + response);
        } catch (FirebaseMessagingException e) {
            if (ERROR_CODE_TOKEN_NOT_REGISTERED.equals(e.getErrorCode())) {
                log.info("Delete action, because token is not registered. Action:\n" + action);
                actionService.deleteById(action.getId());
            } else {
                log.error("Failed to send firebase message", e);
            }
        }
    }

    private Template getTemplate(String eventType, JsonNode config) {
        String titleTemplate = getDefaultTitleTemplate(eventType);
        String bodyTemplate = getDefaultBodyTemplate(eventType);

        if (config.hasNonNull(TEMPLATE_ID)) {
            String templateId = config.get(TEMPLATE_ID).asText();
            titleTemplate = templateService.getTitleTemplate(templateId);
            bodyTemplate = templateService.getBodyTemplate(templateId);
        } else {
            if (config.hasNonNull(TITLE_TEMPLATE)) {
                titleTemplate = config.get(TITLE_TEMPLATE).asText();
            }

            if (config.hasNonNull(BODY_TEMPLATE)) {
                bodyTemplate = config.get(BODY_TEMPLATE).asText();
            }
        }

        return new Template(titleTemplate, bodyTemplate);
    }

    private Map<String, String> prepareCustomData(EventDto dto) {
        if (!StringUtils.startsWith(dto.resolveType(), "task.")) {
            return new HashMap<>();
        }
        TaskEventDto taskEventDTO = EventDtoFactory.fromEventDto(dto);
        Map<String, String> data = new HashMap<>();

        String taskId = taskEventDTO.getTaskInstanceId();
        if (StringUtils.isNotBlank(taskId)) {
            data.put(PARAM_TASK_ID, taskId);
        }

        String doc = taskEventDTO.getDocument();
        if (StringUtils.isNotBlank(doc)) {
            data.put(PARAM_DOCUMENT, doc);
        }

        return data;
    }

    private String getDefaultTitleTemplate(String eventType) {
        switch (TaskEventType.resolve(eventType)) {
            case ASSIGN:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskAssignTitle();
            case CREATE:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskCreateTitle();
            case COMPLETE:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskCompleteTitle();
            case DELETE:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskDeleteTitle();
            default:
                log.warn(String.format("Event type: <%s> not supported", eventType));
                return "unsupported event type";
        }
    }

    private String getDefaultBodyTemplate(String eventType) {
        switch (TaskEventType.resolve(eventType)) {
            case ASSIGN:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskAssignBody();
            case CREATE:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskCreateBody();
            case COMPLETE:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskCompleteBody();
            case DELETE:
                return appProps.getFirebase().getTemplate().getDefaultFirebaseTaskDeleteBody();
            default:
                log.warn(String.format("Event type: <%s> not supported", eventType));
                return "unsupported event type";
        }
    }

    private Message buildAndroidMessage(String title, String body, String registrationToken, Map<String, String> data) {
        return Message.builder()
            .setNotification(new Notification(
                title,
                body))
            .setAndroidConfig(AndroidConfig.builder()
                .setTtl(3600 * 1000)
                .setNotification(AndroidNotification.builder()
                    .setIcon("stock_ticker_update")
                    .setColor("#f45342")
                    .build())
                .build())
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(42)
                    .build())
                .build())
            .putAllData(data)
            .setToken(registrationToken)
            .build();
    }

    private Message buildIosMessage(String title, String body, String registrationToken, Map<String, String> data) {
        return Message.builder()
            .setNotification(new Notification(
                title,
                body))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(42)
                    .build())
                .build())
            .putAllData(data)
            .setToken(registrationToken)
            .build();
    }

    @Getter
    private static class Template {

        Template(String title, String body) {
            this.title = title;
            this.body = body;
        }

        private String title;
        private String body;
    }
}
