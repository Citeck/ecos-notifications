package ru.citeck.ecos.notifications.domain.reminder.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_SOURCE_ID
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_TYPE_ID
import ru.citeck.ecos.notifications.domain.reminder.dto.Reminder
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class ReminderArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService
) : EcosArtifactHandler<Reminder> {

    override fun deployArtifact(artifact: Reminder) {
        AuthContext.runAsSystem {
            recordsService.mutate(EntityRef.create(REMINDER_SOURCE_ID, ""), artifact)
        }
    }

    override fun listenChanges(listener: Consumer<Reminder>) {
        listOf(RecordChangedEvent.TYPE, RecordCreatedEvent.TYPE).forEach { eventType ->
            eventsService.addListener<Reminder> {
                withEventType(eventType)
                withDataClass(Reminder::class.java)
                withFilter(Predicates.eq("typeDef.id", REMINDER_TYPE_ID))
                withAction {
                    listener.accept(it)
                }
            }
        }
    }

    override fun deleteArtifact(artifactId: String) {
        AuthContext.runAsSystem {
            recordsService.delete(EntityRef.create(REMINDER_SOURCE_ID, artifactId))
        }
    }

    override fun getArtifactType(): String {
        return "notification/$REMINDER_TYPE_ID"
    }
}
