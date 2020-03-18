package ru.citeck.ecos.notifications.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.RecordsProperties;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.rest.RemoteRecordsRestApi;
import ru.citeck.ecos.records2.source.dao.remote.RemoteRecordsDAO;

import java.util.Collections;

/**
 * @author Roman Makarskiy
 */
@Configuration
public class RecordsConfig extends RecordsServiceFactory {

    private static final String ALFRESCO_SOURCE_ID = "alfresco";

    @Bean
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
    public RemoteRecordsDAO createAlfrescoRecordsDao(RemoteRecordsRestApi restConnection) {
        RemoteRecordsDAO alfrescoRemote = new RemoteRecordsDAO();
        alfrescoRemote.setRecordsMethod("/alfresco/api/records/query");
        alfrescoRemote.setId(ALFRESCO_SOURCE_ID);
        alfrescoRemote.setRestConnection(restConnection::jsonPost);
        return alfrescoRemote;
    }
}
