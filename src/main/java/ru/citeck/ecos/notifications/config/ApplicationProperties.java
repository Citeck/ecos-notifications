package ru.citeck.ecos.notifications.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.citeck.ecos.commons.data.DataValue;

import java.time.Duration;
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

    @Deprecated
    private final ErrorNotification errorNotification = new ErrorNotification();

    private final Retry retry = new Retry();

    private final AwaitingDispatch awaitingDispatch = new AwaitingDispatch();

    private final BulkMail bulkMail = new BulkMail();

    private final Event event = new Event();

    private final Firebase firebase = new Firebase();

    private final Alfresco alfresco = new Alfresco();

    private final Email email = new Email();

    private final StartupNotification startupNotification = new StartupNotification();

    /**
     * @deprecated use {@link #getRetry()}
     */
    @Deprecated
    public ErrorNotification getErrorNotification() {
        return this.errorNotification;
    }

    public Retry getRetry() {
        return this.retry;
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

    /**
     * @deprecated replaced by {@link Retry} ({@code ecos-notifications.retry.*}).
     * Values set here are accepted for backward compatibility but ignored
     * ({@code ignoreUnknownFields = false} would crash stands that still set them).
     * Overrides are reported with WARN at startup, see {@link RetryPropertiesDeprecationWarner}.
     */
    @Deprecated
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

    /**
     * Retry pipeline for notifications that failed with a transient error
     * ({@link ru.citeck.ecos.notifications.domain.notification.NotificationState#ERROR}).
     * <p>
     * Invariant: {@code leaseTime} must exceed {@code batchSize} × worst-case send timeout
     * (SMTP timeouts are set in {@code application.yml} via
     * {@code spring.mail.properties.mail.smtp.*}), otherwise a claim lease can expire while
     * rows are still being sent and another replica may re-send them.
     */
    public static class Retry {

        /**
         * When disabled, the first failure goes straight to a terminal state (FAILED/EXPIRED)
         * and rows already scheduled before the switch was flipped are expired without a send.
         * The repeater tick itself keeps running, so manually re-driven notifications are still
         * sent (exactly one attempt each).
         */
        private boolean enabled = NotificationsDefault.Retry.ENABLED;

        /**
         * Frequency of the retry job tick (cheap indexed query).
         */
        private Duration pollInterval = NotificationsDefault.Retry.POLL_INTERVAL;

        /**
         * Maximum rows claimed per tick — throttles the recovery storm after an outage.
         * Leftover backlog waits for the next tick.
         */
        private int batchSize = NotificationsDefault.Retry.BATCH_SIZE;

        /**
         * Hard maximum of send attempts; whichever of {@code maxAttempts} / {@code retryWindow}
         * hits first moves the notification to EXPIRED.
         */
        private int maxAttempts = NotificationsDefault.Retry.MAX_ATTEMPTS;

        /**
         * Backoff interval after the first failure.
         */
        private Duration initialInterval = NotificationsDefault.Retry.INITIAL_INTERVAL;

        /**
         * Exponential backoff multiplier: interval(n) = initialInterval * multiplier^(n-1).
         */
        private double multiplier = NotificationsDefault.Retry.MULTIPLIER;

        /**
         * Cap for the backoff interval.
         */
        private Duration maxInterval = NotificationsDefault.Retry.MAX_INTERVAL;

        /**
         * Retry budget counted from the first error ({@code first_error_at}).
         */
        private Duration retryWindow = NotificationsDefault.Retry.RETRY_WINDOW;

        /**
         * Claim lease: claimed rows become visible for other replicas again after this time
         * (protects against a crashed instance losing its claimed batch).
         */
        private Duration leaseTime = NotificationsDefault.Retry.LEASE_TIME;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialInterval() {
            return initialInterval;
        }

        public void setInitialInterval(Duration initialInterval) {
            this.initialInterval = initialInterval;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public Duration getMaxInterval() {
            return maxInterval;
        }

        public void setMaxInterval(Duration maxInterval) {
            this.maxInterval = maxInterval;
        }

        public Duration getRetryWindow() {
            return retryWindow;
        }

        public void setRetryWindow(Duration retryWindow) {
            this.retryWindow = retryWindow;
        }

        public Duration getLeaseTime() {
            return leaseTime;
        }

        public void setLeaseTime(Duration leaseTime) {
            this.leaseTime = leaseTime;
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

        /**
         * Maximum number of notifications fetched and dispatched per scheduled tick. <br>
         * Acts as backpressure against unbounded full-table fetches on the {@code notification}
         * queue. Rows beyond the limit are picked up on the next tick.
         */
        private int batchSize = NotificationsDefault.AwaitingDispatch.BATCH_SIZE;

        public int getDelay() {
            return delay;
        }

        public void setDelay(int delay) {
            this.delay = delay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Event {

        /**
         * Enable legacy events integration.
         * <p>
         * This functionality is likely obsolete and may be removed in the future.
         * However, to ensure backward compatibility, the option to enable it has been retained.
         * Disabled by default.
         */
        private boolean enabled = NotificationsDefault.Event.ENABLED;

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

        public boolean isEnabled() {
            return enabled;
        }

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
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
