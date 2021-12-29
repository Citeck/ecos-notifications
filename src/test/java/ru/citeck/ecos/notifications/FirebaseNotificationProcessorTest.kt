package ru.citeck.ecos.notifications

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Delivery
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.events.data.dto.EventDto
import ru.citeck.ecos.notifications.domain.firebase.*
import ru.citeck.ecos.notifications.domain.subscribe.service.ActionService
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.service.processors.FirebaseNotificationProcessor
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [NotificationsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class FirebaseNotificationProcessorTest {

    companion object {
        private val objectMapper = ObjectMapper()

        private val alfDocRef = RecordRef.valueOf("alfresco/@workspace://SpacesStore/123-aaa")
        private val procDocRef = RecordRef.valueOf("proc/doc@1234")
        private val alfTaskRef = RecordRef.valueOf("alfresco/wftask@test-task")

        private val templateRef = RecordRef.valueOf("notifications/template@test-firebase-message-template")
    }

    @MockBean
    private lateinit var ecosFirebaseService: EcosFirebaseService

    @Autowired
    private lateinit var firebaseNotificationProcessor: FirebaseNotificationProcessor

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var actionService: ActionService

    @Autowired
    private lateinit var localAppService: LocalAppService


    @Before
    fun setUp() {
        localAppService.deployLocalArtifacts()

        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/firebase/test-firebase-message-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create("alfresco/" + alfDocRef.sourceId)
                .addRecord(alfDocRef.id, AlfDocRefAlfrescoDto())
                .build()
        )

        recordsService.register(
            RecordsDaoBuilder.create("proc/" + procDocRef.sourceId)
                .addRecord(procDocRef.id, ProcDocRefDto())
                .build()
        )

        recordsService.register(
            RecordsDaoBuilder.create("alfresco/" + alfTaskRef.sourceId)
                .addRecord(alfTaskRef.id, AlfTaskDto())
                .build()
        )

        Mockito.`when`(ecosFirebaseService.sendMessage(any())).thenReturn(FirebaseMessageResult.OK)

        val all = notificationTemplateService.getAll(100, 0)
        println(all)
    }

    @Test
    fun firebaseNotificationWithoutConfigDeviceTypeShouldRemoveAction() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "true",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        verify(ecosFirebaseService, never()).sendMessage(any())

        val mustBeDeletedAction = actionService.findById(actionEntity.id)
        Assertions.assertThat(mustBeDeletedAction.isPresent).isFalse
    }

    @Test
    fun firebaseNotificationWithoutConfigRegTokenShouldRemoveAction() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "true",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        verify(ecosFirebaseService, never()).sendMessage(any())

        val mustBeDeletedAction = actionService.findById(actionEntity.id)
        Assertions.assertThat(mustBeDeletedAction.isPresent).isFalse
    }

    @Test
    fun firebaseNotificationAnotherAppRefDoc() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "customData.req.attributes.number.asText() == '9002873'",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "$procDocRef",
              "taskInstanceId": "$alfTaskRef"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Proc Документ №9002873",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to procDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithExplicitTrueCondition() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "true",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Alfresco Документ №76342",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithExplicitFalseCondition() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "false",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Alfresco Документ №76342",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService, never()).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithoutCustomData() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "true"
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Alfresco Документ №76342",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithExplicitRuTemplateId() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "ru"
                    },
                    "condition": "customData.req.attributes.number.asText() == '76342'",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Alfresco Документ №76342",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithExplicitEnTemplateId() {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "task.assign",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "templateId": "$templateRef",
                      "locale": "en"
                    },
                    "condition": "customData.req.attributes.number.asText() == '76342'",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "task.assign",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

        val expectedMessage = FirebaseMessage(
            title = "New task: Согласование",
            body = "New task on document: Alfresco Документ №76342",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithDefaultTaskAssignTemplateId() {

        sendFirebaseNotificationWithDefaultTemplateWithType("task.assign")

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Alfresco Документ №76342\n",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithDefaultTaskCreateTemplateId() {

        sendFirebaseNotificationWithDefaultTemplateWithType("task.create")

        val expectedMessage = FirebaseMessage(
            title = "Создана задача: Согласование",
            body = "Alfresco Документ №76342\n",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithDefaultTaskCompleteTemplateId() {

        sendFirebaseNotificationWithDefaultTemplateWithType("task.complete")

        val expectedMessage = FirebaseMessage(
            title = "Завершена задача: Согласование",
            body = "Alfresco Документ №76342\n",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun firebaseNotificationWithDefaultTaskDeleteTemplateId() {

        sendFirebaseNotificationWithDefaultTemplateWithType("task.delete")

        val expectedMessage = FirebaseMessage(
            title = "Удалена задача: Согласование",
            body = "Alfresco Документ №76342\n",
            token = "test-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to alfTaskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to alfDocRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    private fun sendFirebaseNotificationWithDefaultTemplateWithType(eventType: String) {

        val actionRef = recordsService.mutate(
            RecordRef.create("notifications", "subscription-action", ""),
            ObjectData.create(
                """
                {
                  "subscriberId": "some-tenant-id|ivan",
                  "eventType": "$eventType",
                  "action": {
                    "type": "FIREBASE_NOTIFICATION",
                    "config": {
                      "fireBaseClientRegToken": "test-token",
                      "deviceType": "android",
                      "locale": "ru"
                    },
                    "condition": "customData.req.attributes.number.asText() == '76342'",
                    "customData": [
                      {
                        "variable": "req",
                        "record": "$\{event.document}",
                        "attributes": {
                          "number": "documentNumber"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent().replace("\\", "")
            )
        )

        val actionEntity = actionService.findById(actionRef.id.toLong()).get()


        val eventDto = EventDto()
        eventDto.data = objectMapper.readValue(
            """
            {
              "type": "$eventType",
              "id": "event-id-1",
              "document": "${alfDocRef.id}",
              "taskInstanceId": "${alfTaskRef.id}"
            }
        """.trimIndent(), JsonNode::class.java
        )

        val deliveryMessageStub = Delivery(null, null, null)

        firebaseNotificationProcessor.process(deliveryMessageStub, eventDto, actionEntity)

    }

    class AlfDocRefAlfrescoDto(
        @AttName("documentNumber")
        val documentNumber: Long = 76342,

        @AttName("?disp")
        val name: String = "Alfresco Документ №${documentNumber}"
    )

    class AlfTaskDto(
        @AttName("title")
        val name: String = "Согласование"
    )

    class ProcDocRefDto(
        @AttName("documentNumber")
        val documentNumber: Long = 9002873,

        @AttName("?disp")
        val name: String = "Proc Документ №${documentNumber}"
    )

}
