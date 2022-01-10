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
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.FailureNotificationRepository
import java.time.Duration

@RunWith(SpringRunner::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(classes = [NotificationsApp::class])
class HandleFailureMinTryCountNotificationTest {

    @Autowired
    private lateinit var failureNotificationRepository: FailureNotificationRepository

    private lateinit var activeFailureMinTryCount: FailureNotificationEntity
    private lateinit var activeFailureMinTryCountZero: FailureNotificationEntity

    @Before
    fun setup() {

        activeFailureMinTryCount = failureNotificationRepository.save(FailureNotificationEntity(
            tryingCount = -10,
            state = FailureNotificationState.ERROR
        ))

        activeFailureMinTryCountZero = failureNotificationRepository.save(FailureNotificationEntity(
            tryingCount = 0,
            state = FailureNotificationState.ERROR
        ))

    }

    @After
    fun clear() {
        failureNotificationRepository.deleteAll()
    }

    @Test
    fun processFailureMinTryCount() {
        var errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)
        assertThat(errorFailures.size).isEqualTo(2)

        await().atMost(Duration.ofSeconds(40)).untilAsserted {

            errorFailures = failureNotificationRepository.findAllByState(FailureNotificationState.ERROR)

            assertThat(errorFailures.size).isEqualTo(1)
            assertThat(errorFailures[0].id).isEqualTo(activeFailureMinTryCount.id)
        }
    }

}
