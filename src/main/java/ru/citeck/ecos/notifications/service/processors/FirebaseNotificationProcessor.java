package ru.citeck.ecos.notifications.service.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import com.rabbitmq.client.Delivery;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.citeck.ecos.events.data.dto.EventDTO;
import ru.citeck.ecos.events.data.dto.task.TaskEventDTO;
import ru.citeck.ecos.events.data.dto.task.TaskEventType;
import ru.citeck.ecos.notifications.config.ApplicationProperties;
import ru.citeck.ecos.notifications.domain.subscribe.Action;

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

    private static final String PARAM_FIREBASE_CLIENT_REG_TOKEN = "fireBaseClientRegToken";
    private static final String PARAM_DEVICE_TYPE = "deviceType";
    private static final String TITLE_TEMPLATE = "titleTemplate";
    private static final String BODY_TEMPLATE = "bodyTemplate";
    private static final String DEVICE_ANDROID = "android";
    private static final String DEVICE_IOS = "ios";
    private static final String VAR_DATA = "data";
    private static final String PARAM_TASK_ID = "taskId";
    private static final String PARAM_DOCUMENT =  "documentRef";

    private final TemplateEngine templateEngine;
    private final ApplicationProperties appProps;

    {
        setId(ID);
    }

    public FirebaseNotificationProcessor(@Qualifier("eventsTemplateEngine") TemplateEngine templateEngine,
                                         ApplicationProperties appProps) {
        this.templateEngine = templateEngine;
        this.appProps = appProps;
    }

    @Override
    protected void processImpl(Delivery message, EventDTO dto, Action action) {
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

        String eventType = dto.getType();
        String titleTemplate = getDefaultTitleTemplate(eventType);
        String bodyTemplate = getDefaultBodyTemplate(eventType);

        if (config.hasNonNull(TITLE_TEMPLATE)) {
            titleTemplate = config.get(TITLE_TEMPLATE).asText();
        }

        if (config.hasNonNull(BODY_TEMPLATE)) {
            bodyTemplate = config.get(BODY_TEMPLATE).asText();
        }

        Context ctx = new Context();
        ctx.setVariable(VAR_DATA, dto);

        String title = templateEngine.process(titleTemplate, ctx);
        String body = templateEngine.process(bodyTemplate, ctx);

        log.debug(String.format("Processed template: \ntitle from <%s> to <%s>\nbody from <%s> to <%s>",
            titleTemplate, title, bodyTemplate, body));

        String registrationToken = config.get(PARAM_FIREBASE_CLIENT_REG_TOKEN).asText();
        String deviceType = config.get(PARAM_DEVICE_TYPE).asText();

        Message fireBaseMessage;
        Map<String, Object> customData = prepareCustomData(dto);

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
            response = FirebaseMessaging.getInstance().send(fireBaseMessage);
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException("Failed to send firebase message", e);
        }

        log.debug("Successfully sent message: " + response);
    }

    private Map<String, Object> prepareCustomData(EventDTO dto) {
        if (!StringUtils.startsWith(dto.getType(), "task.")) {
            return new HashMap<>();
        }
        TaskEventDTO taskEventDTO = (TaskEventDTO) dto;
        Map<String, Object> data = new HashMap<>();

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

    private Message buildAndroidMessage(String title, String body, String registrationToken, Map<String, Object> data) {
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
                .putAllCustomData(data)
                .setAps(Aps.builder()
                    .setBadge(42)
                    .build())
                .build())
            .setToken(registrationToken)
            .build();
    }

    private Message buildIosMessage(String title, String body, String registrationToken, Map<String, Object> data) {
        return Message.builder()
            .setNotification(new Notification(
                title,
                body))
            .setApnsConfig(ApnsConfig.builder()
                .putAllCustomData(data)
                .setAps(Aps.builder()
                    .setBadge(42)
                    .build())
                .build())
            .setToken(registrationToken)
            .build();
    }
}
