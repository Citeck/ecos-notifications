package ru.citeck.ecos.notifications.config;


public class NotificationsDefault {

    private NotificationsDefault() {
    }

    public class Event {

        private Event() {
        }

        public static final String HOST = "localhost";
        public static final int PORT = 0;
        public static final String USERNAME = "admin";
        public static final String PASSWORD = "admin";

    }

    public class FailureNotification {

        private FailureNotification() {
        }

        public static final int TTL = 86400000;

        public static final int DELAY = 600000;

        public static final int MIN_TRY_COUNT = 10;

    }

    public class Firebase {

        private Firebase() {

        }

        public static final String CREDENTIAL_CLASS_PATH = "";
        public static final String DATA_BASE_URL = "";

        public class Template {

            private Template() {

            }

            public static final String DEFAULT_FIREBASE_TASK_CREATE_TITLE = "Назначена задача";
            public static final String DEFAULT_FIREBASE_TASK_CREATE_BODY = "${event.workflowDescription}";
            public static final String DEFAULT_FIREBASE_TASK_ASSIGN_TITLE = "Назначена задача";
            public static final String DEFAULT_FIREBASE_TASK_ASSIGN_BODY = "${event.workflowDescription}";
            public static final String DEFAULT_FIREBASE_TASK_COMPLETE_TITLE = "Завершена задача";
            public static final String DEFAULT_FIREBASE_TASK_COMPLETE_BODY = "${event.workflowDescription}";
            public static final String DEFAULT_FIREBASE_TASK_DELETE_TITLE = "Удалена задача";
            public static final String DEFAULT_FIREBASE_TASK_DELETE_BODY = "${event.workflowDescription}";

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
