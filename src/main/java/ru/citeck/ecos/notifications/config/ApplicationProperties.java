package ru.citeck.ecos.notifications.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Notifications.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "ecos-notifications", ignoreUnknownFields = false)
public class ApplicationProperties {

    @Getter
    private final Event event = new Event();

    @Getter
    private final Firebase firebase = new Firebase();

    @Getter
    private final Alfresco alfresco = new Alfresco();

    @Getter
    @Setter
    public static class Event {

        private String host = NotificationsDefault.Event.HOST;
        private int port = NotificationsDefault.Event.PORT;
        private String username = NotificationsDefault.Event.USERNAME;
        private String password = NotificationsDefault.Event.PASSWORD;

    }

    @Getter
    @Setter
    public static class Firebase {

        private String credentialClassPath = NotificationsDefault.Firebase.CREDENTIAL_CLASS_PATH;
        private String dataBaseUrl = NotificationsDefault.Firebase.DATA_BASE_URL;
        private final Template template = new Template();

    }

    @Getter
    @Setter
    public static class Template {

        //TODO: get from default real templates
        private String defaultFirebaseTaskCreateTitle = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_CREATE_TITLE;
        private String defaultFirebaseTaskAssignTitle = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_ASSIGN_TITLE;
        private String defaultFirebaseTaskCompleteTitle = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_COMPLETE_TITLE;
        private String defaultFirebaseTaskDeleteTitle = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_DELETE_TITLE;

        private String defaultFirebaseTaskCreateBody = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_CREATE_BODY;
        private String defaultFirebaseTaskAssignBody = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_ASSIGN_BODY;
        private String defaultFirebaseTaskCompleteBody = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_COMPLETE_BODY;
        private String defaultFirebaseTaskDeleteBody = NotificationsDefault.Firebase.Template
            .DEFAULT_FIREBASE_TASK_DELETE_BODY;
    }

    @Getter
    @Setter
    public static class Alfresco {

        private String URL = NotificationsDefault.Alfresco.URL;
        private int connectionTimeout = 5_000;
        private int readTimeout = 60_000;
        private final Authentication authentication = new Authentication();

    }

    @Getter
    @Setter
    public static class Authentication {

        private String username = NotificationsDefault.Alfresco.Authentication.USERNAME;
        private String password = NotificationsDefault.Alfresco.Authentication.PASSWORD;

    }
}
