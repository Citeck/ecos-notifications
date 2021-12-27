package ru.citeck.ecos.notifications.service.processors

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Delivery
import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.apache.commons.lang.StringUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.events.data.dto.EventDto
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory
import ru.citeck.ecos.events.data.dto.task.TaskEventDto
import ru.citeck.ecos.events.data.dto.task.TaskEventType
import ru.citeck.ecos.notifications.config.ApplicationProperties
import ru.citeck.ecos.notifications.domain.firebase.*
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
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
    val appProps: ApplicationProperties
) : ActionProcessor() {

    private val log = KotlinLogging.logger {}

    companion object {
        private const val PARAM_FIREBASE_CLIENT_REG_TOKEN = "fireBaseClientRegToken"
        private const val PARAM_LOCALE = "locale"
        private const val PARAM_TEMPLATE_ID = "templateId"

        private val OBJECT_MAPPER = ObjectMapper()
    }

    init {
        id = "FIREBASE_NOTIFICATION"
    }

    override fun processImpl(message: Delivery, dto: EventDto, action: ActionEntity, model: Map<String, Any>) {

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

        val notification = Notification.Builder()
            .record(notificationTransformer.record())
            .templateRef(notificationTransformer.template())
            .notificationType(NotificationType.FIREBASE_NOTIFICATION)
            .recipients(notificationTransformer.recipients())
            .additionalMeta(notificationTransformer.additionalMeta())
            .build()

        notificationService.send(notification)
    }

    private fun getValidConfig(action: ActionEntity): JsonNode? {
        val config = try {
            OBJECT_MAPPER.readValue(action.configJSON, JsonNode::class.java)
        } catch (e: IOException) {
            log.error("Failed read json from string. Action: $action", e)
            return null
        }

        val regToken = config.get(PARAM_FIREBASE_CLIENT_REG_TOKEN).asText()
        if (regToken.isNullOrBlank()) {
            log.error("Token is empty. Action: $action")
            return null
        }

        val deviceType = config.get(PARAM_FIREBASE_CLIENT_REG_TOKEN).asText()
        if (deviceType.isNullOrBlank()) {
            log.error("Device type is empty. Action: $action")
            return null
        }

        return config
    }

    inner class NotificationTransformer(eventDto: EventDto, config: JsonNode, action: ActionEntity) {

        private val taskEventDto: TaskEventDto

        init {
            taskEventDto = EventDtoFactory.fromEventDto(eventDto)
        }

        val record = fun(): RecordRef {
            return RecordRef.valueOf(taskEventDto.document)
        }

        val template = fun(): RecordRef {
            val templateFromConfig = config[PARAM_TEMPLATE_ID].asText()

            if (templateFromConfig.isNullOrBlank()) return RecordRef.valueOf(templateFromConfig)

            return getDefaultTemplate(eventDto.resolveType())
        }

        val locale = fun(): String {
            val locale = config[PARAM_LOCALE].asText()
            return if (StringUtils.isNotBlank(locale)) locale else "ru"
        }

        val recipients = fun(): Set<String> {
            return setOf(config[PARAM_FIREBASE_CLIENT_REG_TOKEN].asText())
        }

        val additionalMeta = fun(): Map<String, Any> {
            val firebaseMessageData = mutableMapOf<String, String>()

            val taskId = taskEventDto.taskInstanceId
            if (StringUtils.isNotBlank(taskId)) {
                firebaseMessageData[FIREBASE_MESSAGE_DATA_TASK_ID] = taskId
            }

            val doc = taskEventDto.document
            if (StringUtils.isNotBlank(doc)) {
                firebaseMessageData[FIREBASE_MESSAGE_DATA_DOCUMENT] = doc
            }

            val notificationData = mutableMapOf<String, Any>()

            notificationData[FIREBASE_MESSAGE_DATA_KEY] = firebaseMessageData
            notificationData[FIREBASE_DEVICE_TYPE_KEY] =  config[FIREBASE_DEVICE_TYPE_KEY].asText()
            notificationData[FIREBASE_ACTION_ENTITY_ID_KEY] = action.id.toString()

            return mutableMapOf(
                FIREBASE_NOTIFICATION_DATA_KEY to notificationData,
                "task" to RecordRef.create("alfresco", "wftask", taskId)
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


