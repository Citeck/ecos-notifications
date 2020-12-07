package ru.citeck.ecos.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.rest.RemoteRecordsRestApi;
import ru.citeck.ecos.records2.source.dao.remote.RemoteRecordsDao;
import ru.citeck.ecos.records3.RecordsProperties;

import java.util.Collections;

/**
 * @author Roman Makarskiy
 */
@Configuration
public class RecordsConfig {

    private static final String ALFRESCO_SOURCE_ID = "alfresco";

    @Bean
    @ConfigurationProperties(prefix = "ecos-notification.ecos-records")
    public RecordsProperties createRecordsProps(ApplicationProperties appProps) {

        RecordsProperties props = new RecordsProperties();

        RecordsProperties.App alfrescoApp = new RecordsProperties.App();
        RecordsProperties.Authentication auth = new RecordsProperties.Authentication();

        ApplicationProperties.Alfresco alfresco = appProps.getAlfresco();
        ApplicationProperties.Authentication alfrescoAuth = alfresco.getAuthentication();

        auth.setPassword(alfrescoAuth.getPassword());
        auth.setUsername(alfrescoAuth.getUsername());

        alfrescoApp.setAuth(auth);

        props.setApps(Collections.singletonMap(ALFRESCO_SOURCE_ID, alfrescoApp));

        return props;
    }

    @Bean
    @ConfigurationProperties(prefix = "ecos-notification.remote-records-dao")
    public RemoteRecordsDao createAlfrescoRecordsDao(RemoteRecordsRestApi restConnection) {
        RemoteRecordsDao alfrescoRemote = new RemoteRecordsDao();
        alfrescoRemote.setRecordsMethod("/alfresco/api/records/query");
        alfrescoRemote.setId(ALFRESCO_SOURCE_ID);
        alfrescoRemote.setRestConnection(restConnection::jsonPost);
        return alfrescoRemote;
    }
}
