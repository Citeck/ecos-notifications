package ru.citeck.ecos.notifications.config.records;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.citeck.ecos.records2.source.dao.local.InMemRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;
import ru.citeck.ecos.records3.RecordsProperties;

/**
 * @author Roman Makarskiy
 */
@Configuration
public class RecordsConfig {

    @Bean
    @ConfigurationProperties(prefix = "ecos-notification.ecos-records")
    public RecordsProperties createRecordsProps() {
        return new RecordsProperties();
    }

    @Profile("!test")
    @Bean(name = "remoteTypesSyncRecordsDao")
    public InMemRecordsDao<EcosTypeInfo> createRemoteTypesSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/type", "emodel/rtype", EcosTypeInfo.class);
    }

    @Profile("test")
    @Bean(name = "remoteTypesSyncRecordsDao")
    public InMemRecordsDao<EcosTypeInfo> createInMemRemoteTypesSyncRecordsDao() {
        return new InMemRecordsDao<>("emodel/type");
    }

}
