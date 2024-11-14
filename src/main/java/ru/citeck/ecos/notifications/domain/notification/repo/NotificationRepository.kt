package ru.citeck.ecos.notifications.domain.notification.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import java.time.Instant
import java.util.*

interface NotificationRepository :
    JpaRepository<NotificationEntity, Long>,
    JpaSpecificationExecutor<NotificationEntity> {

    @Query(
        value = "select * from notification where state = 'WAIT_FOR_DISPATCH' and (delayed_send is null or :now > delayed_send)",
        nativeQuery = true
    )
    fun findAllToDispatch(
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
}
