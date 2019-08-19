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
@ConfigurationProperties(prefix = "ecos-notifications")
public class ApplicationProperties {

    @Getter
    private final Event event = new Event();

    @Getter
    private final Firebase firebase = new Firebase();

    @Getter
    @Setter
    public static class Event {

        private String host;
        private int port;
        private String username;
        private String password;

    }

    @Getter
    @Setter
    public static class Firebase {

        private String credentialClassPath;
        private String dataBaseUrl;
        private final Template template = new Template();

    }

    @Getter
    @Setter
    public static class Template {

        //TODO: get from default real templates
        private String defaultFirebaseTaskCreateTitle;
        private String defaultFirebaseTaskAssignTitle;
        private String defaultFirebaseTaskCompleteTitle;
        private String defaultFirebaseTaskDeleteTitle;

        private String defaultFirebaseTaskCreateBody;
        private String defaultFirebaseTaskAssignBody;
        private String defaultFirebaseTaskCompleteBody;
        private String defaultFirebaseTaskDeleteBody;
    }
}
