package ru.citeck.ecos.notifications.domain.event.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto

@Service
class NotificationEventService(
    eventsService: EventsService,

    @Value("\${spring.application.name}")
    private val appName: String
) {

    companion object {
        private const val SUCCESS_NOTIFICATION_EVENT_TYPE = "ecos.notification.send.success"
        private const val FAILURE_NOTIFICATION_EVENT_TYPE = "ecos.notification.send.failure"
        private const val BLOCKED_NOTIFICATION_EVENT_TYPE = "ecos.notification.send.blocked"
    }

    private val emitterSuccess = eventsService.getEmitter(EmitterConfig.create<NotificationEventDto> {
        source = appName
        eventType = SUCCESS_NOTIFICATION_EVENT_TYPE
        eventClass = NotificationEventDto::class.java
    })

    private val emitterFailure = eventsService.getEmitter(EmitterConfig.create<NotificationEventDto> {
        source = appName
        eventType = FAILURE_NOTIFICATION_EVENT_TYPE
        eventClass = NotificationEventDto::class.java
    })

    private val emitterBlocked = eventsService.getEmitter(EmitterConfig.create<NotificationEventDto> {
        source = appName
        eventType = BLOCKED_NOTIFICATION_EVENT_TYPE
        eventClass = NotificationEventDto::class.java
    })

    fun emitSendSuccess(notificationEvent: NotificationEventDto) {
        emitterSuccess.emit(notificationEvent)
    }

    fun emitSendFailure(notificationEvent: NotificationEventDto) {
        emitterFailure.emit(notificationEvent)
    }

    fun emitSendBlocked(notificationEvent: NotificationEventDto) {
        emitterBlocked.emit(notificationEvent)
    }


}
