package ru.citeck.ecos.notifications.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Roman Makarskiy
 */
@Component
@Data
@ConfigurationProperties(prefix = "event")
public class EventProps {

    private String host;
    private int port;
    private String username;
    private String password;

}
