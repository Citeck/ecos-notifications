package ru.citeck.ecos.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;

/**
 * Properties specific to Notifications.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "ecos-notifications", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final FailureNotification failureNotification = new FailureNotification();

    private final Event event = new Event();

    private final Firebase firebase = new Firebase();

    private final Alfresco alfresco = new Alfresco();

    public FailureNotification getFailureNotification() {
        return this.failureNotification;
    }

    public Event getEvent() {
        return this.event;
    }

    public Firebase getFirebase() {
        return this.firebase;
    }

    public Alfresco getAlfresco() {
        return this.alfresco;
    }

    public static class FailureNotification {

        private int ttl = NotificationsDefault.FailureNotification.TTL;

        private int delay = NotificationsDefault.FailureNotification.DELAY;

        private int minTryCount = NotificationsDefault.FailureNotification.MIN_TRY_COUNT;

        public int getTtl() {
            return this.ttl;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }

        public int getDelay() {
            return delay;
        }

        public void setDelay(int delay) {
            this.delay = delay;
        }

        public int getMinTryCount() {
            return minTryCount;
        }

        public void setMinTryCount(int minTryCount) {
            this.minTryCount = minTryCount;
        }
    }

    public static class Event {

        private String host = NotificationsDefault.Event.HOST;
        private int port = NotificationsDefault.Event.PORT;
        private String username = NotificationsDefault.Event.USERNAME;
        private String password = NotificationsDefault.Event.PASSWORD;

        public String getHost() {
            return this.host;
        }

        public int getPort() {
            return this.port;
        }

        public String getUsername() {
            return this.username;
        }

        public String getPassword() {
            return this.password;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Firebase {

        private Map<String, Object> credentials = Collections.emptyMap();
        private String credentialClassPath = NotificationsDefault.Firebase.CREDENTIAL_CLASS_PATH;
        private String dataBaseUrl = NotificationsDefault.Firebase.DATA_BASE_URL;
        private final Template template = new Template();

        public String getCredentialClassPath() {
            return this.credentialClassPath;
        }

        public String getDataBaseUrl() {
            return this.dataBaseUrl;
        }

        public Template getTemplate() {
            return this.template;
        }

        public void setCredentialClassPath(String credentialClassPath) {
            this.credentialClassPath = credentialClassPath;
        }

        public void setDataBaseUrl(String dataBaseUrl) {
            this.dataBaseUrl = dataBaseUrl;
        }
    }

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

        public String getDefaultFirebaseTaskCreateTitle() {
            return this.defaultFirebaseTaskCreateTitle;
        }

        public String getDefaultFirebaseTaskAssignTitle() {
            return this.defaultFirebaseTaskAssignTitle;
        }

        public String getDefaultFirebaseTaskCompleteTitle() {
            return this.defaultFirebaseTaskCompleteTitle;
        }

        public String getDefaultFirebaseTaskDeleteTitle() {
            return this.defaultFirebaseTaskDeleteTitle;
        }

        public String getDefaultFirebaseTaskCreateBody() {
            return this.defaultFirebaseTaskCreateBody;
        }

        public String getDefaultFirebaseTaskAssignBody() {
            return this.defaultFirebaseTaskAssignBody;
        }

        public String getDefaultFirebaseTaskCompleteBody() {
            return this.defaultFirebaseTaskCompleteBody;
        }

        public String getDefaultFirebaseTaskDeleteBody() {
            return this.defaultFirebaseTaskDeleteBody;
        }

        public void setDefaultFirebaseTaskCreateTitle(String defaultFirebaseTaskCreateTitle) {
            this.defaultFirebaseTaskCreateTitle = defaultFirebaseTaskCreateTitle;
        }

        public void setDefaultFirebaseTaskAssignTitle(String defaultFirebaseTaskAssignTitle) {
            this.defaultFirebaseTaskAssignTitle = defaultFirebaseTaskAssignTitle;
        }

        public void setDefaultFirebaseTaskCompleteTitle(String defaultFirebaseTaskCompleteTitle) {
            this.defaultFirebaseTaskCompleteTitle = defaultFirebaseTaskCompleteTitle;
        }

        public void setDefaultFirebaseTaskDeleteTitle(String defaultFirebaseTaskDeleteTitle) {
            this.defaultFirebaseTaskDeleteTitle = defaultFirebaseTaskDeleteTitle;
        }

        public void setDefaultFirebaseTaskCreateBody(String defaultFirebaseTaskCreateBody) {
            this.defaultFirebaseTaskCreateBody = defaultFirebaseTaskCreateBody;
        }

        public void setDefaultFirebaseTaskAssignBody(String defaultFirebaseTaskAssignBody) {
            this.defaultFirebaseTaskAssignBody = defaultFirebaseTaskAssignBody;
        }

        public void setDefaultFirebaseTaskCompleteBody(String defaultFirebaseTaskCompleteBody) {
            this.defaultFirebaseTaskCompleteBody = defaultFirebaseTaskCompleteBody;
        }

        public void setDefaultFirebaseTaskDeleteBody(String defaultFirebaseTaskDeleteBody) {
            this.defaultFirebaseTaskDeleteBody = defaultFirebaseTaskDeleteBody;
        }
    }

    public static class Alfresco {

        private String URL = NotificationsDefault.Alfresco.URL;
        private int connectionTimeout = 5_000;
        private int readTimeout = 60_000;
        private final Authentication authentication = new Authentication();

        public String getURL() {
            return this.URL;
        }

        public int getConnectionTimeout() {
            return this.connectionTimeout;
        }

        public int getReadTimeout() {
            return this.readTimeout;
        }

        public Authentication getAuthentication() {
            return this.authentication;
        }

        public void setURL(String URL) {
            this.URL = URL;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Authentication {

        private String username = NotificationsDefault.Alfresco.Authentication.USERNAME;
        private String password = NotificationsDefault.Alfresco.Authentication.PASSWORD;

        public String getUsername() {
            return this.username;
        }

        public String getPassword() {
            return this.password;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
