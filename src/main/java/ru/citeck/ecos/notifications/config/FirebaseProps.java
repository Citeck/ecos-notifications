package ru.citeck.ecos.notifications.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Roman Makarskiy
 */
@Component
@Data
@ConfigurationProperties(prefix = "firebase")
public class FirebaseProps {

    private String credentialClassPath;
    private String dataBaseUrl;

}
