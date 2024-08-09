package ru.citeck.ecos.notifications.domain.sender.repo

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import ru.citeck.ecos.notifications.lib.NotificationType
import java.time.Instant

@Entity
@Table(name = "notifications_sender")
class NotificationsSenderEntity @JvmOverloads constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "ecos_notifications_sender_id_gen")
    var id: Long? = null,

    @NotNull
    @Column(unique = true, nullable = false)
    var extId: String? = null,

    var name: String? = null,

    var enabled: Boolean = false,

    @Enumerated(EnumType.STRING)
    var notificationType: NotificationType? = null,

    @Column(name = "sender_order")
    var order: Float? = null,
    /**
     * Value has to match with one of the NotificationSender.getSenderType() implementation
     */
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
        const val PROP_ID = "id"
        const val PROP_ENABLED = "enabled"
        const val PROP_NOTIFICATION_TYPE = "notificationType"
        const val PROP_ORDER = "order"
    }
}
