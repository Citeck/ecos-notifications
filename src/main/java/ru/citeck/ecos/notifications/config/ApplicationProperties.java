package ru.citeck.ecos.notifications.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.citeck.ecos.commons.data.DataValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final Email email = new Email();

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

    public Email getEmail() {
        return email;
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

        private String credentialClassPath = NotificationsDefault.Firebase.CREDENTIAL_CLASS_PATH;
        private String dataBaseUrl = NotificationsDefault.Firebase.DATA_BASE_URL;
        private final Template template = new Template();
        private final Credentials credentials = new Credentials();

        public String getCredentialClassPath() {
            return this.credentialClassPath;
        }

        public String getDataBaseUrl() {
            return this.dataBaseUrl;
        }

        public Template getTemplate() {
            return this.template;
        }

        public Credentials getCredentials() {
            return credentials;
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

    public static class Credentials {

        private String type = "";
        private String projectId = "";
        private String privateKeyId = "";
        private String privateKey = "";
        private String clientEmail = "";
        private String clientId = "";
        private String authUri = "";
        private String tokenUri = "";
        private String authProviderX509CertUrl = "";
        private String clientX509CertUrl = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getPrivateKeyId() {
            return privateKeyId;
        }

        public void setPrivateKeyId(String privateKeyId) {
            this.privateKeyId = privateKeyId;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getClientEmail() {
            return clientEmail;
        }

        public void setClientEmail(String clientEmail) {
            this.clientEmail = clientEmail;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getAuthUri() {
            return authUri;
        }

        public void setAuthUri(String authUri) {
            this.authUri = authUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getAuthProviderX509CertUrl() {
            return authProviderX509CertUrl;
        }

        public void setAuthProviderX509CertUrl(String authProviderX509CertUrl) {
            this.authProviderX509CertUrl = authProviderX509CertUrl;
        }

        public String getClientX509CertUrl() {
            return clientX509CertUrl;
        }

        public void setClientX509CertUrl(String clientX509CertUrl) {
            this.clientX509CertUrl = clientX509CertUrl;
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

    public static class Email {

        private EmailFrom from = new EmailFrom();

        public void setDataFromOther(Email email) {
            this.from = new EmailFrom(email.from);
        }

        public EmailFrom getFrom() {
            return from;
        }

        public void setFrom(EmailFrom from) {
            this.from = from;
        }
    }

    public static class EmailFrom {

        private String defaultEmail = "";
        private String fixed = "";
        private Map<String, String> mapping = new LinkedHashMap<>();

        public EmailFrom() {
        }

        public EmailFrom(EmailFrom other) {
            this.defaultEmail = other.defaultEmail;
            this.fixed = other.fixed;
            this.mapping = DataValue.create(other.mapping).asMap(String.class, String.class);
        }

        public void setDefault(String value) {
            this.defaultEmail = value;
        }

        public String getDefault() {
            return defaultEmail;
        }

        public String getFixed() {
            return fixed;
        }

        public void setFixed(String fixed) {
            this.fixed = fixed;
        }

        public Map<String, String> getMapping() {
            return mapping;
        }

        public void setMapping(List<EmailMapping> mapping) {
            this.mapping = new LinkedHashMap<>();
            mapping.forEach(it -> this.mapping.put(it.getSource(), it.getTarget()));
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmailMapping {
        private String source;
        private String target;
    }
}
