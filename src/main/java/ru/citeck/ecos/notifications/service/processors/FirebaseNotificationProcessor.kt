package ru.citeck.ecos.notifications.service.processors

import com.rabbitmq.client.Delivery
import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events.data.dto.EventDto
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory
import ru.citeck.ecos.events.data.dto.task.TaskEventDto
import ru.citeck.ecos.events.data.dto.task.TaskEventType
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.firebase.*
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.RecordRef
import java.io.IOException

/**
 *  //TODO: Make the class universal, at the moment it is possible to send notifications only about the TaskEvent event
 *
 * @author Roman Makarskiy
 */

@Slf4j
@Component
class FirebaseNotificationProcessor(
    val notificationService: NotificationService,
    val actionService: ActionService,
    val notificationTemplateService: NotificationTemplateService,
    val appProps: ApplicationProperties
) : ActionProcessor() {

    private val log = KotlinLogging.logger {}

    companion object {
        private const val WORKSPACE_SPACES_STORE = "workspace://SpacesStore/"
        private const val ALFRESCO_APP = "alfresco"
        private const val WF_TASK_SOURCE_ID = "wftask"
    }

    init {
        id = "FIREBASE_NOTIFICATION"
    }

    @Value("\${notifications.default.locale}")
    private lateinit var defaultLocale: String

    override fun processImpl(message: Delivery, dto: EventDto, action: ActionEntity, model: Map<String, Any>) {
        AuthContext.runAsSystem {
            sendFirebaseNotification(dto, action)
        }
    }

    private fun sendFirebaseNotification(dto: EventDto, action: ActionEntity) {
        log.debug("FirebaseNotificationProcessor Process start. DTO: \n$dto")

        val config = getValidConfig(action)
        if (config == null) {
            log.error(
                "Firebase notification not send, because config is not valid. " +
                    "Action ${action.id} will be deleted."
            )
            actionService.deleteById(action.id)
            return
        }

        val notificationTransformer = NotificationTransformer(dto, config, action)

        val templateRef = notificationTransformer.template()
        val templateWithMeta = notificationTemplateService.findById(templateRef.id)
        if (!templateWithMeta.isPresent) {
            log.error(
                "Firebase notification not send, because template with id " +
                    "$templateRef not found. " +
                    "Action ${action.id} will be deleted."
            )
            actionService.deleteById(action.id)
            return
        }

        val notification = Notification.Builder()
            .record(notificationTransformer.record())
            .templateRef(notificationTransformer.template())
            .notificationType(NotificationType.FIREBASE_NOTIFICATION)
            .recipients(notificationTransformer.recipients())
            .additionalMeta(notificationTransformer.additionalMeta())
            .lang(notificationTransformer.locale())
            .build()

        log.debug { "Build notification: \n$notification" }

        notificationService.send(notification)
    }

    private fun getValidConfig(action: ActionEntity): ObjectData? {
        val config = try {
            ObjectData.create(action.configJSON)
        } catch (e: IOException) {
            log.error("Failed read json from string. Action: $action", e)
            return null
        }

        val regToken = config.get(FIREBASE_CONFIG_CLIENT_REG_TOKEN).asText()
        if (regToken.isBlank()) {
            log.error("Token is empty. Action: $action")
            return null
        }

        val deviceType = config.get(FIREBASE_CONFIG_DEVICE_TYPE_KEY).asText()
        if (deviceType.isBlank()) {
            log.error("Device type is empty. Action: $action")
            return null
        }

        return config
    }

    inner class NotificationTransformer(eventDto: EventDto, config: ObjectData, action: ActionEntity) {

        private val taskEventDto: TaskEventDto

        init {
            taskEventDto = EventDtoFactory.fromEventDto(eventDto)
        }

        val record = fun(): RecordRef {
            if (taskEventDto.document.isNullOrBlank()) {
                return RecordRef.EMPTY
            }

            val ref = RecordRef.valueOf(taskEventDto.document)
            if (ref.appName.isBlank() && ref.id.startsWith(WORKSPACE_SPACES_STORE)) {
                return RecordRef.create(ALFRESCO_APP, "", ref.id)
            }

            return ref
        }

        val task = fun(): RecordRef {
            if (taskEventDto.taskInstanceId.isNullOrBlank()) {
                return RecordRef.EMPTY
            }

            val ref = RecordRef.valueOf(taskEventDto.taskInstanceId)
            val appName = ref.appName.ifBlank { ALFRESCO_APP }
            val sourceId = ref.sourceId.ifBlank { WF_TASK_SOURCE_ID }

            return RecordRef.create(appName, sourceId, ref.id)
        }

        val template = fun(): RecordRef {
            config.get(FIREBASE_CONFIG_TEMPLATE_ID).asText().let { templateId ->
                return if (templateId.isBlank()) {
                    getDefaultTemplate(eventDto.resolveType())
                } else {
                    RecordRef.valueOf(templateId)
                }
            }
        }

        val locale = fun(): String {
            return config.get(FIREBASE_CONFIG_LOCALE).asText().ifBlank {
                defaultLocale
            }
        }

        val recipients = fun(): Set<String> {
            config.get(FIREBASE_CONFIG_CLIENT_REG_TOKEN).asText().let { regToken ->
                return if (regToken.isBlank()) {
                    emptySet()
                } else {
                    setOf(regToken)
                }
            }
        }

        val additionalMeta = fun(): Map<String, Any> {
            val firebaseMessageData = mutableMapOf<String, String>()

            firebaseMessageData[FIREBASE_MESSAGE_DATA_TASK_ID] = task().toString()
            firebaseMessageData[FIREBASE_MESSAGE_DATA_DOCUMENT] = record().toString()

            val notificationData = mutableMapOf<String, Any>()

            notificationData[FIREBASE_MESSAGE_DATA_KEY] = firebaseMessageData
            notificationData[FIREBASE_CONFIG_DEVICE_TYPE_KEY] = config.get(FIREBASE_CONFIG_DEVICE_TYPE_KEY).asText()
            notificationData[FIREBASE_ACTION_ENTITY_ID_KEY] = action.id

            return mutableMapOf(
                FIREBASE_NOTIFICATION_DATA_KEY to notificationData,
                FIREBASE_NOTIFICATION_TASK_KEY to task()
            )
        }

        private fun getDefaultTemplate(eventType: String): RecordRef {
            return when (TaskEventType.resolve(eventType)) {
                TaskEventType.ASSIGN -> RecordRef.valueOf(appProps.firebase.template.defaultTaskAssignTemplate)
                TaskEventType.CREATE -> RecordRef.valueOf(appProps.firebase.template.defaultTaskCreateTemplate)
                TaskEventType.COMPLETE -> RecordRef.valueOf(appProps.firebase.template.defaultTaskCompleteTemplate)
                TaskEventType.DELETE -> RecordRef.valueOf(appProps.firebase.template.defaultTaskDeleteTemplate)
                else -> {
                    throw EcosFirebaseNotificationException("Event type <$eventType> not supported")
                }
            }
        }
    }

}


