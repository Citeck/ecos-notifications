package ru.citeck.ecos.notifications.config.records;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean(name = "remoteTypesSyncRecordsDao")
    public RemoteSyncRecordsDao<EcosTypeInfo> createRemoteTypesSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/type", "emodel/rtype", EcosTypeInfo.class);
    }

}
