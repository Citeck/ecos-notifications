package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import java.time.Duration

@RunWith(SpringRunner::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(classes = [NotificationsApp::class])
class HandleFailureMinTryCountNotificationTest {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var activeFailureMinTryCount: NotificationEntity
    private lateinit var activeFailureMinTryCountZero: NotificationEntity

    @Before
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

    @After
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
