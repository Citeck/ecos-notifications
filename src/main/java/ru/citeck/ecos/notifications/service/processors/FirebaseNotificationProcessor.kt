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
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.notifications.service.providers.ACTION_ENTITY_ID
import ru.citeck.ecos.notifications.service.providers.DEVICE_TYPE_KEY
import ru.citeck.ecos.notifications.service.providers.FirebaseNotificationException
import ru.citeck.ecos.notifications.service.providers.MESSAGE_DATA_KEY
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
    val appProps: ApplicationProperties
) : ActionProcessor() {

    private val log = KotlinLogging.logger {}

    companion object {
        private val OBJECT_MAPPER = ObjectMapper()
        private const val PARAM_FIREBASE_CLIENT_REG_TOKEN = "fireBaseClientRegToken"
        private const val PARAM_DEVICE_TYPE = "deviceType"
        private const val PARAM_LOCALE = "locale"
        private const val PARAM_TEMPLATE_ID = "templateId"
        private const val PARAM_TASK_ID = "taskId"
        private const val PARAM_DOCUMENT = "documentRef"
    }

    init {
        id = "FIREBASE_NOTIFICATION"
    }

    override fun processImpl(message: Delivery, dto: EventDto, action: ActionEntity, model: Map<String, Any>) {

        log.debug("FirebaseNotificationProcessor Process start. DTO: \n$dto")

        val notificationTransformer = NotificationTransformer(dto, action)

        val notification = Notification.Builder()
            .record(notificationTransformer.record())
            .templateRef(notificationTransformer.template())
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(notificationTransformer.recipients())
            .additionalMeta(notificationTransformer.additionalMeta())
            .build()

        notificationService.send(notification)
    }

    inner class NotificationTransformer(eventDto: EventDto, action: ActionEntity) {

        private val taskEventDto: TaskEventDto
        private val config: JsonNode

        init {
            config = try {
                OBJECT_MAPPER.readValue(action.configJSON, JsonNode::class.java)
            } catch (e: IOException) {
                throw RuntimeException("Failed read json from string", e)
            }

            check(config.hasNonNull(PARAM_FIREBASE_CLIENT_REG_TOKEN)) {
                "Action config does not contains firebase registration token"
            }
            check(config.hasNonNull(PARAM_DEVICE_TYPE)) { "Action config does not contains device type" }

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
            val messageData = mutableMapOf<String, String>()
            val taskId = taskEventDto.taskInstanceId
            if (StringUtils.isNotBlank(taskId)) {
                messageData[PARAM_TASK_ID] = taskId
            }

            val doc = taskEventDto.document
            if (StringUtils.isNotBlank(doc)) {
                messageData[PARAM_DOCUMENT] = doc
            }

            return mutableMapOf(
                MESSAGE_DATA_KEY to messageData,
                DEVICE_TYPE_KEY to config[PARAM_DEVICE_TYPE].asText(),
                ACTION_ENTITY_ID to action.id,
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
                    throw FirebaseNotificationException("Event type <$eventType> not supported")
                }
            }
        }
    }

}


