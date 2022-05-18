package ru.citeck.ecos.notifications.domain.sender.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import ru.citeck.ecos.notifications.lib.NotificationType
import java.time.Instant
import javax.persistence.*
import javax.validation.constraints.NotNull

@Entity
@Table(name = "notifications_sender")
class NotificationsSenderEntity @JvmOverloads constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_notifications_sender_id_gen")
    @SequenceGenerator(name = "ecos_notifications_sender_id_gen")
    var id: Long? = null,

    @NotNull
    @Column(unique = true, nullable = false)
    var extId: String? = null,
    var enabled: Boolean = false,
    @Enumerated(EnumType.STRING)
    var notificationType: NotificationType? = null,
    var order: Float? = null,
    var senderType: String? = null,
    var condition: String? = null,
    var templates: String? = null,
    var senderConfig: String? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()

) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate) {
    companion object {
        const val ID = "id"
    }
}
