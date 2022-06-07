package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Duration

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class HandleFailureMinTryCountNotificationTest {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var activeFailureMinTryCount: NotificationEntity
    private lateinit var activeFailureMinTryCountZero: NotificationEntity

    @BeforeEach
    fun setup() {

        activeFailureMinTryCount = notificationRepository.save(
            NotificationEntity(
                tryingCount = -10,
                state = NotificationState.ERROR
            )
        )

        activeFailureMinTryCountZero = notificationRepository.save(
            NotificationEntity(
                tryingCount = 0,
                state = NotificationState.ERROR
            )
        )
    }

    @AfterEach
    fun clear() {
        notificationRepository.deleteAll()
    }

    @Test
    fun processFailureMinTryCount() {
        var errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)
        assertThat(errorFailures.size).isEqualTo(2)

        await().atMost(Duration.ofSeconds(40)).untilAsserted {

            errorFailures = notificationRepository.findAllByState(NotificationState.ERROR)

            assertThat(errorFailures.size).isEqualTo(1)
            assertThat(errorFailures[0].id).isEqualTo(activeFailureMinTryCount.id)
        }
    }
}
