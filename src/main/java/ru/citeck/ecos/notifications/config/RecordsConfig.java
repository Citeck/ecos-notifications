package ru.citeck.ecos.notifications.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records2.source.dao.RecordsDAO;

import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Configuration
public class RecordsConfig extends RecordsServiceFactory {

    @Bean
    public RecordsService createRecordsService(List<RecordsDAO> recordsDao) {
        RecordsService recordsService = super.createRecordsService();
        recordsDao.forEach(recordsService::register);
        return recordsService;
    }

    @Bean
    public RestHandler formRestQueryHandler(RecordsService recordsService) {
        return new RestHandler(recordsService);
    }

}
