package ru.citeck.ecos.notifications.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.events2.EventService
import ru.citeck.ecos.events2.EventServiceFactory
import ru.citeck.ecos.records3.RecordsServiceFactory

@Configuration
class EventServiceDummyConfig(
    recordsServiceFactory: RecordsServiceFactory
) : EventServiceFactory(recordsServiceFactory) {

    @Bean
    override fun createEventService(): EventService {
        return super.createEventService()
    }
}
