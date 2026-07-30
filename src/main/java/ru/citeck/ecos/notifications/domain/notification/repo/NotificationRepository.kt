package ru.citeck.ecos.notifications.domain.notification.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import java.time.Instant
import java.util.*

interface NotificationRepository :
    JpaRepository<NotificationEntity, Long>,
    JpaSpecificationExecutor<NotificationEntity> {

    @Query(
        value = "select * from notification where state = 'WAIT_FOR_DISPATCH' " +
            "and (delayed_send is null or :now > delayed_send) " +
            "limit :limit",
        nativeQuery = true
    )
    fun findAllToDispatch(
        @Param("limit") limit: Int,
        @Param("now") now: Instant = Instant.now()
    ): List<NotificationEntity>

    fun findAllByState(state: NotificationState): List<NotificationEntity>

    fun findOneByExtId(extId: String): Optional<NotificationEntity>

    @Query(
        "select state as state, count(*) as count from notification where bulk_mail_ref = :bulkMailRef group by state",
        nativeQuery = true
    )
    fun getNotificationStateSummaryForBulkMail(
        @Param("bulkMailRef") bulkMailRef: String
    ): List<BulkNotificationStateSummaryProjection>

    fun findAllByBulkMailRef(bulkMailRef: String): List<NotificationEntity>

    fun findByBulkMailRefAndStateIs(bulkMailRef: String, state: NotificationState): List<NotificationEntity>

    fun findAllByRecord(recordRef: String): List<NotificationEntity>

    /**
     * Claims due ERROR rows for retry by pushing next_retry_at forward to the lease deadline.
     * FOR UPDATE SKIP LOCKED makes concurrent replicas skip each other's rows; a crashed
     * instance's rows become visible again when the lease expires — no SENDING state needed.
     *
     * An unscheduled ERROR row (`next_retry_at is null`) counts as due and is claimed first.
     * The policy always fills the schedule, so such a row is an anomaly — a row written by an
     * old replica during a rolling upgrade, or one restored from a backup taken before the
     * retry columns existed. Excluding it (SQL NULL never satisfies `<= :now`) would strand it
     * forever: never retried, never terminal, its bulk mail pinned in TRYING_TO_DISPATCH and
     * the backlog gauge permanently inflated — exactly the silent loss this pipeline prevents.
     */
    @Query(
        value = "update notification set next_retry_at = :leaseUntil " +
            "where id in (" +
            "  select id from notification" +
            "  where state = 'ERROR' and (next_retry_at is null or next_retry_at <= :now)" +
            "  order by next_retry_at nulls first" +
            "  for update skip locked" +
            "  limit :batch" +
            ") returning id",
        nativeQuery = true
    )
    fun claimErrorsForRetry(
        @Param("now") now: Instant,
        @Param("leaseUntil") leaseUntil: Instant,
        @Param("batch") batch: Int
    ): List<Long>

    /**
     * Size of the retry backlog — rows waiting for another attempt. Kept as a native count
     * so PostgreSQL can answer it from the partial index `idx_notification_next_retry_at`
     * (`where state = 'ERROR'`) instead of scanning the table.
     */
    @Query(
        value = "select count(*) from notification where state = 'ERROR'",
        nativeQuery = true
    )
    fun countErrorBacklog(): Long

    /**
     * Atomically cancels every not-yet-delivered notification of a bulk mail. Covers both
     * queued rows (WAIT_FOR_DISPATCH) and rows in the retry pipeline (ERROR) — a single
     * conditional UPDATE, so no row can slip into ERROR between read and save. Rows claimed
     * by the repeater mid-flight are protected by [updateIfStateStillError].
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "update notification set " +
            "state = 'CANCELLED', " +
            "next_retry_at = null, " +
            "last_modified_date = :now " +
            "where bulk_mail_ref = :bulkMailRef and state in ('WAIT_FOR_DISPATCH', 'ERROR')",
        nativeQuery = true
    )
    fun cancelDeferredForBulkMail(
        @Param("bulkMailRef") bulkMailRef: String,
        @Param("now") now: Instant
    ): Int

    /**
     * Writes the outcome of a synchronous send failure, but only while the row has not been
     * cancelled meanwhile. The cancellation (bulk mail deleted while the attempt was in flight)
     * and this write race each other, so the CANCELLED check must happen in the same statement:
     * a read-then-save would put the row back into the retry pipeline for its full budget and
     * keep mailing recipients of a deleted bulk mail.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "update notification set " +
            "state = :state, " +
            "type = :type, " +
            "record = :record, " +
            "template = :template, " +
            "web_url = :webUrl, " +
            "data = :data, " +
            "trying_count = :tryingCount, " +
            "last_trying_date = :lastTryingDate, " +
            "next_retry_at = :nextRetryAt, " +
            "first_error_at = :firstErrorAt, " +
            "failure_kind = :failureKind, " +
            "error_message = :errorMessage, " +
            "error_stack_trace = :errorStackTrace, " +
            "last_modified_date = :now " +
            "where id = :id and state <> 'CANCELLED'",
        nativeQuery = true
    )
    fun updateFailureIfNotCancelled(
        @Param("id") id: Long,
        @Param("state") state: String,
        @Param("type") type: String?,
        @Param("record") record: String?,
        @Param("template") template: String?,
        @Param("webUrl") webUrl: String?,
        @Param("data") data: ByteArray?,
        @Param("tryingCount") tryingCount: Int,
        @Param("lastTryingDate") lastTryingDate: Instant?,
        @Param("nextRetryAt") nextRetryAt: Instant?,
        @Param("firstErrorAt") firstErrorAt: Instant?,
        @Param("failureKind") failureKind: String?,
        @Param("errorMessage") errorMessage: String?,
        @Param("errorStackTrace") errorStackTrace: String?,
        @Param("now") now: Instant
    ): Int

    /**
     * Manual re-drive: puts a failed row back into the retry pipeline with a fresh budget.
     * Only rows in a retryable state are touched, so a concurrent successful send or a
     * cancellation is never undone.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "update notification set " +
            "state = 'ERROR', " +
            "trying_count = 0, " +
            "first_error_at = null, " +
            "failure_kind = null, " +
            "next_retry_at = :now, " +
            "last_modified_date = :now " +
            "where id = :id and state in ('ERROR', 'FAILED', 'EXPIRED')",
        nativeQuery = true
    )
    fun redriveForRetry(
        @Param("id") id: Long,
        @Param("now") now: Instant
    ): Int

    /**
     * Writes the outcome of a retry attempt back, but only while the row still belongs to the
     * claim the attempt was made under. `next_retry_at = :claimedUntil` is the claim token: a
     * re-drive (which rewrites next_retry_at to "now") and a re-claim by another replica (which
     * rewrites it to its own lease deadline) both invalidate it, so a stale result is dropped
     * instead of undoing them. The state check additionally covers cancellation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "update notification set " +
            "state = :state, " +
            "trying_count = :tryingCount, " +
            "last_trying_date = :lastTryingDate, " +
            "next_retry_at = :nextRetryAt, " +
            "first_error_at = :firstErrorAt, " +
            "failure_kind = :failureKind, " +
            "error_message = :errorMessage, " +
            "error_stack_trace = :errorStackTrace, " +
            "last_modified_date = :now " +
            "where id = :id and state = 'ERROR' and next_retry_at = :claimedUntil",
        nativeQuery = true
    )
    fun updateIfStateStillError(
        @Param("id") id: Long,
        @Param("claimedUntil") claimedUntil: Instant,
        @Param("state") state: String,
        @Param("tryingCount") tryingCount: Int,
        @Param("lastTryingDate") lastTryingDate: Instant?,
        @Param("nextRetryAt") nextRetryAt: Instant?,
        @Param("firstErrorAt") firstErrorAt: Instant?,
        @Param("failureKind") failureKind: String?,
        @Param("errorMessage") errorMessage: String?,
        @Param("errorStackTrace") errorStackTrace: String?,
        @Param("now") now: Instant
    ): Int
}
