package ru.citeck.ecos.notifications.domain.event.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.events2.EventService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.notifications.domain.event.dto.NotificationEventDto
import ru.citeck.ecos.records3.record.request.RequestContext

@Service
class NotificationEventService(
    eventService: EventService,

    @Value("\${spring.application.name}")
    private val appName: String
) {

    companion object {
        private const val SUCCESS_NOTIFICATION_EVENT_TYPE = "ecos.notification.send.success"
        private const val FAILURE_NOTIFICATION_EVENT_TYPE = "ecos.notification.send.failure"
        private const val CURRENT_USER_ATT = "currentUser"
    }

    private val emitterSuccess = eventService.getEmitter(EmitterConfig.create<NotificationEventDto> {
        source = appName
        eventType = SUCCESS_NOTIFICATION_EVENT_TYPE
        eventClass = NotificationEventDto::class.java
    })

    private val emitterFailure = eventService.getEmitter(EmitterConfig.create<NotificationEventDto> {
        source = appName
        eventType = FAILURE_NOTIFICATION_EVENT_TYPE
        eventClass = NotificationEventDto::class.java
    })

    fun emitSendSuccess(notificationEvent: NotificationEventDto, currentUser: String) {
        RequestContext.doWithAtts(
            mapOf(
                CURRENT_USER_ATT to currentUser,
            )
        ) { _ ->
            emitterSuccess.emit(notificationEvent)
        }
    }

    fun emitSendFailure(notificationEvent: NotificationEventDto, currentUser: String) {
        RequestContext.doWithAtts(
            mapOf(
                CURRENT_USER_ATT to currentUser,
            )
        ) { _ ->
            emitterFailure.emit(notificationEvent)
        }
    }

}
