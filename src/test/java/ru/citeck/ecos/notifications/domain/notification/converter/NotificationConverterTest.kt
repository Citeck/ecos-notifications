package ru.citeck.ecos.notifications.domain.notification.converter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.records3.RecordsService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class NotificationConverterTest {

    private lateinit var notificationRepository: NotificationRepository

    @BeforeEach
    fun setUp() {
        notificationRepository = mock()
        whenever(notificationRepository.findOneByExtId(any())).thenReturn(Optional.empty())

        val converter = NotificationConverter(mock<RecordsService>(), notificationRepository)
        val init = NotificationConverter::class.java.getDeclaredMethod("init")
        init.isAccessible = true
        init.invoke(converter)
    }

    @Test
    fun entityToDtoToEntityKeepsRetryFields() {
        val nextRetryAt = Instant.now().plus(5, ChronoUnit.MINUTES)
        val firstErrorAt = Instant.now().minus(1, ChronoUnit.HOURS)

        val entity = NotificationEntity(
            id = 42L,
            extId = "test-ext-id",
            type = NotificationType.EMAIL_NOTIFICATION,
            tryingCount = 3,
            lastTryingDate = Instant.now(),
            nextRetryAt = nextRetryAt,
            firstErrorAt = firstErrorAt,
            failureKind = FailureKind.TRANSIENT,
            state = NotificationState.ERROR
        )

        val dto = entity.toDto()

        assertThat(dto.nextRetryAt).isEqualTo(nextRetryAt)
        assertThat(dto.firstErrorAt).isEqualTo(firstErrorAt)
        assertThat(dto.failureKind).isEqualTo(FailureKind.TRANSIENT)

        val restored = dto.toEntity()

        assertThat(restored.nextRetryAt).isEqualTo(nextRetryAt)
        assertThat(restored.firstErrorAt).isEqualTo(firstErrorAt)
        assertThat(restored.failureKind).isEqualTo(FailureKind.TRANSIENT)
        assertThat(restored.state).isEqualTo(NotificationState.ERROR)
        assertThat(restored.tryingCount).isEqualTo(3)
    }

    @Test
    fun entityToDtoToEntityKeepsNullRetryFields() {
        val entity = NotificationEntity(
            id = 43L,
            extId = "test-ext-id-2",
            state = NotificationState.SENT
        )

        val dto = entity.toDto()

        assertThat(dto.nextRetryAt).isNull()
        assertThat(dto.firstErrorAt).isNull()
        assertThat(dto.failureKind).isNull()

        val restored = dto.toEntity()

        assertThat(restored.nextRetryAt).isNull()
        assertThat(restored.firstErrorAt).isNull()
        assertThat(restored.failureKind).isNull()
    }

    @Test
    fun dtoWithFailedStateConvertsToEntity() {
        val entity = NotificationEntity(
            id = 44L,
            extId = "test-ext-id-3",
            failureKind = FailureKind.PERMANENT,
            state = NotificationState.FAILED
        )

        val dto = entity.toDto()

        assertThat(dto.state).isEqualTo(NotificationState.FAILED)
        assertThat(dto.failureKind).isEqualTo(FailureKind.PERMANENT)

        val restored = dto.toEntity()

        assertThat(restored.state).isEqualTo(NotificationState.FAILED)
        assertThat(restored.failureKind).isEqualTo(FailureKind.PERMANENT)
    }
}
