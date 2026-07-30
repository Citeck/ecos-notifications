package ru.citeck.ecos.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.notifications.domain.notification.FailureKind
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Verifies the retry claim mechanics: [NotificationDao.claimErrorsForRetry] leases due ERROR
 * rows (pushing next_retry_at forward so subsequent claims skip them) and
 * [NotificationDao.saveIfStateStillError] never overwrites rows that already left ERROR.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class NotificationRetryClaimTest {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var notificationDao: NotificationDao

    @BeforeEach
    fun setup() {
        notificationRepository.deleteAll()
    }

    @AfterEach
    fun clear() {
        notificationRepository.deleteAll()
    }

    private fun errorRow(nextRetryAt: Instant?, state: NotificationState = NotificationState.ERROR): NotificationEntity {
        return notificationRepository.save(
            NotificationEntity(
                state = state,
                tryingCount = 1,
                nextRetryAt = nextRetryAt,
                firstErrorAt = Instant.now().minus(1, ChronoUnit.HOURS),
                failureKind = FailureKind.TRANSIENT
            )
        )
    }

    @Test
    fun `sequential claims do not return the same rows`() {
        val now = Instant.now()
        val oldest = errorRow(now.minus(30, ChronoUnit.MINUTES))
        val middle = errorRow(now.minus(20, ChronoUnit.MINUTES))
        val newest = errorRow(now.minus(10, ChronoUnit.MINUTES))

        val lease = Duration.ofMinutes(15)

        val firstClaim = AuthContext.runAsSystem { notificationDao.claimErrorsForRetry(2, lease) }
        assertThat(firstClaim.map { it.id }).containsExactlyInAnyOrder(oldest.id, middle.id)

        val secondClaim = AuthContext.runAsSystem { notificationDao.claimErrorsForRetry(2, lease) }
        assertThat(secondClaim.map { it.id }).containsExactly(newest.id)

        val thirdClaim = AuthContext.runAsSystem { notificationDao.claimErrorsForRetry(2, lease) }
        assertThat(thirdClaim).isEmpty()
    }

    @Test
    fun `claim pushes next retry at forward by lease`() {
        errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))

        val before = Instant.now()
        val claimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(15))
        }

        assertThat(claimed).hasSize(1)
        assertThat(claimed[0].nextRetryAt).isAfterOrEqualTo(before.plus(15, ChronoUnit.MINUTES))

        val persisted = notificationRepository.findById(claimed[0].id!!).get()
        assertThat(persisted.nextRetryAt).isEqualTo(claimed[0].nextRetryAt)
    }

    @Test
    fun `claim skips rows scheduled in the future and non error states`() {
        errorRow(Instant.now().plus(10, ChronoUnit.MINUTES))
        errorRow(Instant.now().minus(10, ChronoUnit.MINUTES), state = NotificationState.CANCELLED)

        val claimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(15))
        }

        assertThat(claimed).isEmpty()
    }

    /**
     * An ERROR row without a schedule cannot be produced by the current policy, but an old replica
     * writing during a rolling upgrade can. Skipping it (SQL NULL never satisfies `<= now`) would
     * strand it forever — never retried, never terminal — so it counts as due, and first.
     */
    @Test
    fun `claim treats an unscheduled error row as due and prioritizes it`() {
        val scheduled = errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))
        val unscheduled = errorRow(nextRetryAt = null)

        val claimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(1, Duration.ofMinutes(15))
        }

        assertThat(claimed.map { it.id }).containsExactly(unscheduled.id)
        assertThat(claimed[0].nextRetryAt).isNotNull()

        val rest = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(15))
        }
        assertThat(rest.map { it.id }).containsExactly(scheduled.id)
    }

    @Test
    fun `claim returns rows again once the lease expired`() {
        val row = errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))

        // a lease that is already in the past emulates an instance that crashed mid-batch
        val expiredLease = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(-5))
        }
        assertThat(expiredLease.map { it.id }).containsExactly(row.id)

        val reclaimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(15))
        }
        assertThat(reclaimed.map { it.id }).containsExactly(row.id)
    }

    @Test
    fun `error backlog count sees only rows in error state`() {
        errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))
        errorRow(Instant.now().plus(1, ChronoUnit.HOURS))
        errorRow(Instant.now(), state = NotificationState.SENT)
        errorRow(Instant.now(), state = NotificationState.FAILED)
        errorRow(Instant.now(), state = NotificationState.EXPIRED)

        assertThat(notificationDao.getErrorBacklogCount()).isEqualTo(2)
    }

    @Test
    fun `saveIfStateStillError updates a row that is still claimed`() {
        errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))
        val claimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(15))
        }[0]

        val updated = AuthContext.runAsSystem {
            notificationDao.saveIfStateStillError(
                claimed.copy(
                    state = NotificationState.SENT,
                    tryingCount = 2,
                    nextRetryAt = null,
                    failureKind = null
                ),
                claimed.nextRetryAt
            )
        }

        assertThat(updated).isTrue()
        val persisted = notificationRepository.findById(claimed.id!!).get()
        assertThat(persisted.state).isEqualTo(NotificationState.SENT)
        assertThat(persisted.tryingCount).isEqualTo(2)
        assertThat(persisted.nextRetryAt).isNull()
        assertThat(persisted.failureKind).isNull()
    }

    @Test
    fun `saveIfStateStillError does not overwrite cancelled row`() {
        val row = errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))
        val staleDto = row.toDto()

        row.state = NotificationState.CANCELLED
        notificationRepository.save(row)

        val updated = AuthContext.runAsSystem {
            notificationDao.saveIfStateStillError(
                staleDto.copy(state = NotificationState.SENT, tryingCount = 2),
                staleDto.nextRetryAt
            )
        }

        assertThat(updated).isFalse()
        val persisted = notificationRepository.findById(row.id!!).get()
        assertThat(persisted.state).isEqualTo(NotificationState.CANCELLED)
        assertThat(persisted.tryingCount).isEqualTo(1)
    }

    /**
     * A re-drive (or a re-claim by another replica) rewrites next_retry_at, which invalidates the
     * claim the in-flight attempt was made under — its result must not undo the fresh budget.
     */
    @Test
    fun `saveIfStateStillError does not overwrite a row re-driven after the claim`() {
        errorRow(Instant.now().minus(1, ChronoUnit.MINUTES))
        val claimed = AuthContext.runAsSystem {
            notificationDao.claimErrorsForRetry(10, Duration.ofMinutes(15))
        }[0]

        AuthContext.runAsSystem { notificationDao.redriveForRetry(claimed.id!!) }

        val updated = AuthContext.runAsSystem {
            notificationDao.saveIfStateStillError(
                claimed.copy(state = NotificationState.EXPIRED, tryingCount = 5, nextRetryAt = null),
                claimed.nextRetryAt
            )
        }

        assertThat(updated).isFalse()
        val persisted = notificationRepository.findById(claimed.id!!).get()
        assertThat(persisted.state).isEqualTo(NotificationState.ERROR)
        assertThat(persisted.tryingCount).isEqualTo(0)
        assertThat(persisted.nextRetryAt).isNotNull()
    }

    @Test
    fun `saveFailureIfNotCancelled writes the failure of a live row`() {
        val row = errorRow(nextRetryAt = null, state = NotificationState.WAIT_FOR_DISPATCH)
        val nextRetryAt = Instant.now().plus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS)

        val updated = AuthContext.runAsSystem {
            notificationDao.saveFailureIfNotCancelled(
                row.toDto().copy(
                    state = NotificationState.ERROR,
                    tryingCount = 1,
                    errorMessage = "smtp is down",
                    nextRetryAt = nextRetryAt
                )
            )
        }

        assertThat(updated).isTrue()
        val persisted = notificationRepository.findById(row.id!!).get()
        assertThat(persisted.state).isEqualTo(NotificationState.ERROR)
        assertThat(persisted.tryingCount).isEqualTo(1)
        assertThat(persisted.errorMessage).isEqualTo("smtp is down")
        assertThat(persisted.nextRetryAt).isEqualTo(nextRetryAt)
    }

    /**
     * The synchronous failure path reads the row, classifies the failure and only then writes the
     * outcome. A bulk mail deleted in that window cancels the row, and an unconditional save would
     * put it back into the retry pipeline for its full budget — so the CANCELLED check happens in
     * the same statement as the write.
     */
    @Test
    fun `saveFailureIfNotCancelled drops the failure of a row cancelled meanwhile`() {
        val row = errorRow(nextRetryAt = null, state = NotificationState.WAIT_FOR_DISPATCH)
        val staleDto = row.toDto()

        row.state = NotificationState.CANCELLED
        notificationRepository.save(row)

        val updated = AuthContext.runAsSystem {
            notificationDao.saveFailureIfNotCancelled(
                staleDto.copy(
                    state = NotificationState.ERROR,
                    tryingCount = 2,
                    nextRetryAt = Instant.now()
                )
            )
        }

        assertThat(updated).isFalse()
        val persisted = notificationRepository.findById(row.id!!).get()
        assertThat(persisted.state).isEqualTo(NotificationState.CANCELLED)
        assertThat(persisted.tryingCount).isEqualTo(1)
        assertThat(persisted.nextRetryAt).isNull()
    }
}
