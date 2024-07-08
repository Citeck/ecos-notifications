package ru.citeck.ecos.notifications.domain.notification.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.stringJsonFromResource
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationTemplateConverterTest {

    @Autowired
    private lateinit var notificationTemplateConverter: NotificationTemplateConverter

    @Autowired
    private lateinit var notificationTemplateService: NotificationTemplateService

    @MockBean
    private lateinit var notificationDao: NotificationDao

    @BeforeEach
    fun setUp() {
        val notificationTemplate = Json.mapper.convert(
            stringJsonFromResource("template/converter/converter-test-template.json"),
            NotificationTemplateWithMeta::class.java
        )!!

        notificationTemplateService.save(notificationTemplate)
    }

    @Test
    fun convertToReadableNotificationTest() {
        val readableNotification = "Hi John, your last name is Smith? You are 30 old?"
        val testNotificationModel =
            "{\"model\": {\"initiator.firstName?str\": \"John\", \"initiator.lastName?str\": \"Smith\", \"initiator.age?num\": 30}}"

        val notification = NotificationDto(
            null,
            "",
            RecordRef.EMPTY,
            RecordRef.EMPTY,
            "",
            null,
            Json.mapper.toBytes(testNotificationModel),
            "",
            "",
            RecordRef.EMPTY,
            null,
            0,
            null,
            RecordRef.EMPTY,
            NotificationState.SENT,
            null,
            Instant.now(),
            null,
            Instant.now()
        )

        whenever(notificationDao.getById(any())).thenReturn(notification)

        val result = notificationTemplateConverter.convertToReadableNotification(
            100,
            RecordRef.valueOf("notifications/template@converter-test-template")
        )
        assertEquals(readableNotification, result.trim())
    }
}
