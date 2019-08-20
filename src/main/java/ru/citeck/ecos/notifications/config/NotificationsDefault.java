package ru.citeck.ecos.notifications.config;


public class NotificationsDefault {

    private NotificationsDefault() {
    }

    public class Event {

        private Event() {
        }

        public static final String HOST = "localhost";
        public static final int PORT = 0;
        public static final String USERNAME = "";
        public static final String PASSWORD = "";

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
            public static final String DEFAULT_FIREBASE_TASK_CREATE_BODY = "[(${data.workflowDescription})]";
            public static final String DEFAULT_FIREBASE_TASK_ASSIGN_TITLE = "Назначена задача";
            public static final String DEFAULT_FIREBASE_TASK_ASSIGN_BODY = "[(${data.workflowDescription})]";
            public static final String DEFAULT_FIREBASE_TASK_COMPLETE_TITLE = "Завершена задача";
            public static final String DEFAULT_FIREBASE_TASK_COMPLETE_BODY = "[(${data.workflowDescription})]";
            public static final String DEFAULT_FIREBASE_TASK_DELETE_TITLE = "Удалена задача";
            public static final String DEFAULT_FIREBASE_TASK_DELETE_BODY = "[(${data.workflowDescription})]";

        }

    }

}
