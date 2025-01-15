package ru.citeck.ecos.notifications.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.citeck.ecos.commons.data.DataValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Properties specific to Notifications.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 */
@ConfigurationProperties(prefix = "ecos-notifications", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final ErrorNotification errorNotification = new ErrorNotification();

    private final AwaitingDispatch awaitingDispatch = new AwaitingDispatch();

    private final BulkMail bulkMail = new BulkMail();

    private final Event event = new Event();

    private final Firebase firebase = new Firebase();

    private final Alfresco alfresco = new Alfresco();

    private final Email email = new Email();

    private final StartupNotification startupNotification = new StartupNotification();

    public ErrorNotification getErrorNotification() {
        return this.errorNotification;
    }

    public AwaitingDispatch getAwaitingDispatch() {
        return awaitingDispatch;
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

    public BulkMail getBulkMail() {
        return bulkMail;
    }

    public StartupNotification getStartupNotification() {
        return startupNotification;
    }

    public static class ErrorNotification {

        /**
         * Time to live (milliseconds) of error notifications. <br>
         * The exact countdown is the time the notification was created. <br>
         * After the time has elapsed, the notification is transferred to the expired status
         * {@link ru.citeck.ecos.notifications.domain.notification.NotificationState#EXPIRED},
         * no more sending attempts.
         * For infinity ttl use -1
         */
        private int ttl = NotificationsDefault.ErrorNotification.TTL;

        /**
         * Frequency (milliseconds) of the job on resending notification with an error status -
         * {@link ru.citeck.ecos.notifications.domain.notification.NotificationState#ERROR}
         */
        private int delay = NotificationsDefault.ErrorNotification.DELAY;

        /**
         * Minimum trying count of attempts to resend notification. <br>
         * Times of specified attempts will be made to send a message, regardless of the ttl.
         */
        private int minTryCount = NotificationsDefault.ErrorNotification.MIN_TRY_COUNT;

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

    public static class BulkMail {

        private int syncStatusDelay = NotificationsDefault.BulkMail.SYNC_STATUS_DELAY;

        public int getSyncStatusDelay() {
            return syncStatusDelay;
        }

        public void setSyncStatusDelay(int syncStatusDelay) {
            this.syncStatusDelay = syncStatusDelay;
        }
    }

    public static class AwaitingDispatch {

        /**
         * Frequency (milliseconds) of the job on dispatch notifications with a wait for dispatch status -
         * {@link ru.citeck.ecos.notifications.domain.notification.NotificationState#WAIT_FOR_DISPATCH}
         */
        private int delay = NotificationsDefault.AwaitingDispatch.DELAY;

        public int getDelay() {
            return delay;
        }

        public void setDelay(int delay) {
            this.delay = delay;
        }
    }

    public static class Event {

        /**
         * Host for connect to ecos-events rabbitmq.
         */
        private String host = NotificationsDefault.Event.HOST;

        /**
         * Port for connect to ecos-events rabbitmq.
         */
        private int port = NotificationsDefault.Event.PORT;

        /**
         * Username for connect to ecos-events rabbitmq.
         */
        private String username = NotificationsDefault.Event.USERNAME;

        /**
         * Password for connect to ecos-events rabbitmq.
         */
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

        private String defaultTaskCreateTemplate = NotificationsDefault.Firebase.Template
            .DEFAULT_TASK_CREATE_TEMPLATE;
        private String defaultTaskAssignTemplate = NotificationsDefault.Firebase.Template
            .DEFAULT_TASK_ASSIGN_TEMPLATE;
        private String defaultTaskCompleteTemplate = NotificationsDefault.Firebase.Template
            .DEFAULT_TASK_COMPLETE_TEMPLATE;
        private String defaultTaskDeleteTemplate = NotificationsDefault.Firebase.Template
            .DEFAULT_TASK_DELETE_TEMPLATE;

        public String getDefaultTaskCreateTemplate() {
            return this.defaultTaskCreateTemplate;
        }

        public String getDefaultTaskAssignTemplate() {
            return this.defaultTaskAssignTemplate;
        }

        public String getDefaultTaskCompleteTemplate() {
            return this.defaultTaskCompleteTemplate;
        }

        public String getDefaultTaskDeleteTemplate() {
            return this.defaultTaskDeleteTemplate;
        }

        public void setDefaultTaskCreateTemplate(String defaultTaskCreateTemplate) {
            this.defaultTaskCreateTemplate = defaultTaskCreateTemplate;
        }

        public void setDefaultTaskAssignTemplate(String defaultTaskAssignTemplate) {
            this.defaultTaskAssignTemplate = defaultTaskAssignTemplate;
        }

        public void setDefaultTaskCompleteTemplate(String defaultTaskCompleteTemplate) {
            this.defaultTaskCompleteTemplate = defaultTaskCompleteTemplate;
        }

        public void setDefaultTaskDeleteTemplate(String defaultTaskDeleteTemplate) {
            this.defaultTaskDeleteTemplate = defaultTaskDeleteTemplate;
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StartupNotification {
        private boolean enabled;
        private String body;
        private String title;
        private String recipient;

        public boolean isEnabled() {
            return enabled;
        }

        public String getBody() {
            return body;
        }

        public String getTitle() {
            return title;
        }

        public String getRecipient() {
            return recipient;
        }
    }
}
