package ru.citeck.ecos.notifications.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.EventsServiceFactory

@Configuration
class EventServiceDummyConfig : EventsServiceFactory() {

    @Bean
    override fun createEventsService(): EventsService {
        return super.createEventsService()
    }
}
