package ru.citeck.ecos.notifications.config;

import java.time.Duration;

public class NotificationsDefault {

    private NotificationsDefault() {
    }

    public class Event {

        private Event() {
        }

        public static final boolean ENABLED = false;
        public static final String HOST = "localhost";
        public static final int PORT = 0;
        public static final String USERNAME = "admin";
        public static final String PASSWORD = "admin";

    }

    /**
     * @deprecated replaced by {@link Retry}, values are ignored
     */
    @Deprecated
    public class ErrorNotification {

        private ErrorNotification() {
        }

        public static final int TTL = 86400000;

        public static final int DELAY = 600000;

        public static final int MIN_TRY_COUNT = 10;

    }

    public static class Retry {

        private Retry() {
        }

        public static final boolean ENABLED = true;
        public static final Duration POLL_INTERVAL = Duration.ofSeconds(30);
        public static final int BATCH_SIZE = 50;
        public static final int MAX_ATTEMPTS = 20;
        public static final Duration INITIAL_INTERVAL = Duration.ofMinutes(1);
        public static final double MULTIPLIER = 3.0;
        public static final Duration MAX_INTERVAL = Duration.ofHours(2);
        public static final Duration RETRY_WINDOW = Duration.ofHours(24);
        public static final Duration LEASE_TIME = Duration.ofMinutes(15);

    }

    public class BulkMail {

        private BulkMail() {
        }

        public static final int SYNC_STATUS_DELAY = 7_000;

    }

    public class AwaitingDispatch {

        private AwaitingDispatch() {
        }

        public static final int DELAY = 5_000;
        public static final int BATCH_SIZE = 1_000;

    }

    public class Firebase {

        private Firebase() {

        }

        public static final String CREDENTIAL_CLASS_PATH = "";
        public static final String DATA_BASE_URL = "";

        public class Template {

            private Template() {

            }

            public static final String DEFAULT_TASK_CREATE_TEMPLATE = "notifications/template@default-task-create-firbase-message-template";
            public static final String DEFAULT_TASK_ASSIGN_TEMPLATE = "notifications/template@default-task-assign-firbase-message-template";
            public static final String DEFAULT_TASK_COMPLETE_TEMPLATE = "notifications/template@default-task-complete-firbase-message-template";
            public static final String DEFAULT_TASK_DELETE_TEMPLATE = "notifications/template@default-task-delete-firbase-message-template";

        }

    }

    public class Alfresco {

        private Alfresco() {

        }

        public static final String URL = "http://localhost:8080";

        public class Authentication {

            private Authentication() {

            }

            public static final String USERNAME = "admin";
            public static final String PASSWORD = "admin";

        }

    }

}
