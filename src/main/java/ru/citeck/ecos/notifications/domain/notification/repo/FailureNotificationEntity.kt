package ru.citeck.ecos.notifications.domain.notification.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState
import java.io.Serializable
import java.time.Instant
import javax.persistence.*
import javax.validation.constraints.NotNull

@Entity
@Table(name = "failure_notification")
class FailureNotificationEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    var id: Long? = null,

    var data: ByteArray? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "error_stack_trace")
    var errorStackTrace: String? = null,

    var tryingCount: Int? = 0,

    var lastTryingDate: Instant? = null,

    @get: NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: FailureNotificationState? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()
) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FailureNotificationEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return 31
    }

    override fun toString(): String {
        return "FailureNotificationEntity(id=$id," +
            " tryingCount=$tryingCount, lastTryingDate=$lastTryingDate, state=$state)"
    }


}
