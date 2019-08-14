package ru.citeck.ecos.notifications.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Roman Makarskiy
 */
@Component
@Data
@ConfigurationProperties(prefix = "notification.template")
public class TemplateProps {

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
