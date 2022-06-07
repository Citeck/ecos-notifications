package ru.citeck.ecos.notifications

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.firebase.*
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class FirebaseNotificationTest {

    companion object {
        private val taskRef = RecordRef.valueOf("task@test-task")
        private val docRef = RecordRef.valueOf("doc@test-document")
        private val templateRef = RecordRef.valueOf("notifications/template@test-firebase-message-template")
    }

    @MockBean
    private lateinit var ecosFirebaseService: EcosFirebaseService

    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @Autowired
    private lateinit var recordsService: RecordsService

    val additionalMetaWithDevice = fun(deviceType: DeviceType): Map<String, Any> {
        val additionalMeta = mutableMapOf<String, Any>()

        val firebaseMessageData = mapOf(
            FIREBASE_MESSAGE_DATA_TASK_ID to taskRef.toString(),
            FIREBASE_MESSAGE_DATA_DOCUMENT to docRef.toString(),
        )

        val messageData = mapOf(
            FIREBASE_CONFIG_DEVICE_TYPE_KEY to deviceType.value,
            FIREBASE_MESSAGE_DATA_KEY to firebaseMessageData
        )

        additionalMeta["task"] = taskRef
        additionalMeta[FIREBASE_NOTIFICATION_DATA_KEY] = messageData

        return additionalMeta
    }

    @BeforeEach
    fun setUp() {
        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/firebase/test-firebase-message-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)

        recordsService.register(
            RecordsDaoBuilder.create(taskRef.sourceId)
                .addRecord(taskRef.id, TaskDto())
                .build()
        )

        recordsService.register(
            RecordsDaoBuilder.create(docRef.sourceId)
                .addRecord(docRef.id, DocDto())
                .build()
        )

        Mockito.`when`(ecosFirebaseService.sendMessage(any())).thenReturn(
            FirebaseMessageResult(FirebaseMessageResultCode.OK)
        )
    }

    @Test
    fun sendFirebaseRuAndroidMessageTest() {
        val notification = Notification.Builder()
            .record(docRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.FIREBASE_NOTIFICATION)
            .recipients(listOf("fire-base-token"))
            .additionalMeta(additionalMetaWithDevice(DeviceType.ANDROID))
            .lang("ru")
            .build()

        notificationService.send(notification)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Документ №123",
            token = "fire-base-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to taskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to docRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun sendFirebaseEnAndroidMessageTest() {
        val notification = Notification.Builder()
            .record(docRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.FIREBASE_NOTIFICATION)
            .recipients(listOf("fire-base-token"))
            .additionalMeta(additionalMetaWithDevice(DeviceType.ANDROID))
            .lang("en")
            .build()

        notificationService.send(notification)

        val expectedMessage = FirebaseMessage(
            title = "New task: Согласование",
            body = "New task on document: Документ №123",
            token = "fire-base-token",
            deviceType = DeviceType.ANDROID,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to taskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to docRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun sendFirebaseRuIosMessageTest() {
        val notification = Notification.Builder()
            .record(docRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.FIREBASE_NOTIFICATION)
            .recipients(listOf("fire-base-token"))
            .additionalMeta(additionalMetaWithDevice(DeviceType.IOS))
            .lang("ru")
            .build()

        notificationService.send(notification)

        val expectedMessage = FirebaseMessage(
            title = "Новая задача: Согласование",
            body = "Новая задача по документу: Документ №123",
            token = "fire-base-token",
            deviceType = DeviceType.IOS,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to taskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to docRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    @Test
    fun sendFirebaseEnIosMessageTest() {
        val notification = Notification.Builder()
            .record(docRef)
            .templateRef(templateRef)
            .notificationType(NotificationType.FIREBASE_NOTIFICATION)
            .recipients(listOf("fire-base-token"))
            .additionalMeta(additionalMetaWithDevice(DeviceType.IOS))
            .lang("en")
            .build()

        notificationService.send(notification)

        val expectedMessage = FirebaseMessage(
            title = "New task: Согласование",
            body = "New task on document: Документ №123",
            token = "fire-base-token",
            deviceType = DeviceType.IOS,
            messageData = mapOf(
                FIREBASE_MESSAGE_DATA_TASK_ID to taskRef.toString(),
                FIREBASE_MESSAGE_DATA_DOCUMENT to docRef.toString()
            )
        )

        verify(ecosFirebaseService).sendMessage(expectedMessage)
    }

    class TaskDto(
        @AttName("title")
        val name: String = "Согласование"
    )

    class DocDto(
        @AttName("?disp")
        val name: String = "Документ №123"
    )
}
