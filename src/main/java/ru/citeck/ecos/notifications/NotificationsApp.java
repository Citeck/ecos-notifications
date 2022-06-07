package ru.citeck.ecos.notifications;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import ru.citeck.ecos.notifications.config.ApplicationProperties;
import ru.citeck.ecos.webapp.lib.spring.EcosSpringApplication;

@SpringBootApplication
@EnableConfigurationProperties({LiquibaseProperties.class, ApplicationProperties.class})
@EnableJpaRepositories("ru.citeck.ecos.notifications.domain.*.repo")
@EnableDiscoveryClient
public class NotificationsApp {

    public static final String NAME = "notifications";

    /**
     * Main method, used to run the application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new EcosSpringApplication(NotificationsApp.class).run(args);
    }
}
