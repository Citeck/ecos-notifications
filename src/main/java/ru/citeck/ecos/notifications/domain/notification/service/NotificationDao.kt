package ru.citeck.ecos.notifications.domain.notification.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toDto
import ru.citeck.ecos.notifications.domain.notification.converter.toEntity
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct

@Service
@Transactional
class NotificationDao(
    private val notificationRepository: NotificationRepository,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory,
    private val workspaceService: WorkspaceService
) {

    companion object {
        /**
         * States a notification can be re-driven from — must stay in sync with the state
         * filter of [NotificationRepository.redriveForRetry].
         */
        val RETRYABLE_STATES = setOf(
            NotificationState.ERROR,
            NotificationState.FAILED,
            NotificationState.EXPIRED
        )
    }

    private lateinit var searchConv: JpaSearchConverter<NotificationEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(NotificationEntity::class.java).build()
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun save(dto: NotificationDto): NotificationDto {
        return notificationRepository.save(dto.toEntity()).toDto()
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun saveAll(dto: List<NotificationDto>): List<NotificationDto> {
        return notificationRepository.saveAll(dto.map { it.toEntity() }).map { it.toDto() }
    }

    /**
     * Claims due ERROR rows for retry in a short standalone transaction: pushes their
     * next_retry_at forward by [lease] and returns the claimed rows. Sending must happen
     * OUTSIDE this transaction — the lease (not a long transaction) is what protects the
     * rows from other replicas.
     */
    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimErrorsForRetry(batch: Int, lease: Duration): List<NotificationDto> {
        val now = Instant.now()
        val claimedIds = notificationRepository.claimErrorsForRetry(now, now.plus(lease), batch)
        if (claimedIds.isEmpty()) {
            return emptyList()
        }
        return notificationRepository.findAllById(claimedIds).map { it.toDto() }
    }

    /**
     * Conditional save of a retry outcome: applied only while the row still belongs to the claim
     * the attempt was made under. Rows that were cancelled, re-driven or re-claimed by another
     * replica meanwhile are left alone. Returns true if the row was updated.
     *
     * @param claimedUntil the lease deadline the row was claimed with
     * ([claimErrorsForRetry] stores it in `nextRetryAt`), used as the claim token.
     */
    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun saveIfStateStillError(dto: NotificationDto, claimedUntil: Instant?): Boolean {
        val id = requireNotNull(dto.id) { "Cannot update notification without id: $dto" }
        val claimToken = requireNotNull(claimedUntil) { "Cannot update notification without a claim: $dto" }
        val updated = notificationRepository.updateIfStateStillError(
            id,
            claimToken,
            dto.state.name,
            dto.tryingCount,
            dto.lastTryingDate,
            dto.nextRetryAt,
            dto.firstErrorAt,
            dto.failureKind?.name,
            dto.errorMessage,
            dto.errorStackTrace,
            Instant.now()
        )
        return updated > 0
    }

    /**
     * Conditional save of a synchronous send failure ([NotificationCommandResultHolder.holdError]):
     * applied only while the row has not been cancelled meanwhile. A row whose bulk mail was
     * deleted while the attempt was in flight must not be put back into the retry pipeline, and
     * the CANCELLED check cannot be a separate read — the cancellation happens concurrently.
     * Returns true if the row was updated.
     */
    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun saveFailureIfNotCancelled(dto: NotificationDto): Boolean {
        val id = requireNotNull(dto.id) { "Cannot update notification without id: $dto" }
        val updated = notificationRepository.updateFailureIfNotCancelled(
            id,
            dto.state.name,
            dto.type?.name,
            dto.record.toString(),
            dto.template.toString(),
            dto.webUrl,
            dto.data,
            dto.tryingCount,
            dto.lastTryingDate,
            dto.nextRetryAt,
            dto.firstErrorAt,
            dto.failureKind?.name,
            dto.errorMessage,
            dto.errorStackTrace,
            Instant.now()
        )
        return updated > 0
    }

    /**
     * Manual re-drive of a failed notification: resets the retry budget
     * (tryingCount, firstErrorAt, failureKind) and schedules the row for immediate pickup by
     * [ErrorNotificationRepeater]. Applied only to rows still in a retryable state
     * ([RETRYABLE_STATES]), so a row finalized concurrently is left alone — returns false then.
     */
    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun redriveForRetry(id: Long): Boolean {
        return notificationRepository.redriveForRetry(id, Instant.now()) > 0
    }

    /**
     * Atomically cancels all not-yet-delivered notifications of a bulk mail
     * (WAIT_FOR_DISPATCH and ERROR). Returns the number of cancelled rows.
     * ERROR rows already claimed by the repeater are covered by [saveIfStateStillError]:
     * once the state here becomes CANCELLED, their in-flight retry result is dropped.
     */
    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun cancelDeferredForBulkMail(bulkMailRef: String): Int {
        return notificationRepository.cancelDeferredForBulkMail(bulkMailRef, Instant.now())
    }

    /**
     * Number of notifications waiting for another retry attempt. Cheap indexed count,
     * used by the `notifications.retry.backlog` gauge ([NotificationRetryMetrics]).
     */
    @Transactional(readOnly = true)
    fun getErrorBacklogCount(): Long {
        return notificationRepository.countErrorBacklog()
    }

    @Transactional(readOnly = true)
    fun findAllToDispatch(limit: Int): List<NotificationDto> {
        return notificationRepository.findAllToDispatch(limit).map { it.toDto() }.toList()
    }

    @Transactional(readOnly = true)
    fun getByExtId(extId: String): NotificationDto? {
        val found = notificationRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else {
            null
        }
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): NotificationDto? {
        val found = notificationRepository.findById(id)

        return if (found.isPresent) {
            found.get().toDto()
        } else {
            null
        }
    }

    @Transactional(readOnly = true)
    fun getBulkMailStateSummary(bulkMailRef: String): Map<NotificationState, Long> {
        return notificationRepository.getNotificationStateSummaryForBulkMail(bulkMailRef)
            .associateBy({ NotificationState.valueOf(it.getState()) }, { it.getCount() })
    }

    @Transactional(readOnly = true)
    fun findByRecord(recordRef: String): List<NotificationDto> {
        return notificationRepository.findAllByRecord(recordRef).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun findNotificationForBulkMail(bulkMailRef: String): List<NotificationDto> {
        return notificationRepository.findAllByBulkMailRef(bulkMailRef).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun findNotificationForBulkMail(bulkMailRef: String, state: NotificationState): List<NotificationDto> {
        return notificationRepository.findByBulkMailRefAndStateIs(bulkMailRef, state).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getAll(
        max: Int,
        skip: Int,
        predicate: Predicate,
        workspaces: List<String>,
        sort: List<SortBy>
    ): List<NotificationDto> {
        val predicateWithWorkspaces = Predicates.and(
            workspaceService.buildAvailableWorkspacesPredicate(AuthContext.getCurrentRunAsAuth(), workspaces),
            predicate
        )
        return searchConv.findAll(notificationRepository, predicateWithWorkspaces, max, skip, sort).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(): List<NotificationDto> {
        return notificationRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int): List<NotificationDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return notificationRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getCount(predicate: Predicate, workspaces: List<String>): Long {
        val predicateWithWorkspaces = Predicates.and(
            workspaceService.buildAvailableWorkspacesPredicate(AuthContext.getCurrentRunAsAuth(), workspaces),
            predicate
        )
        return searchConv.getCount(notificationRepository, predicateWithWorkspaces)
    }

    private fun getCount(): Long {
        return notificationRepository.count()
    }
}
