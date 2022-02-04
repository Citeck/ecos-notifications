package ru.citeck.ecos.notifications.domain.bulkmail.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import ru.citeck.ecos.notifications.lib.NotificationType
import java.io.Serializable
import java.time.Instant
import javax.persistence.*
import javax.validation.constraints.NotNull

@Entity
@Table(name = "bulk_mail")
class BulkMailEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_bulk_mail_id_gen")
    @SequenceGenerator(name = "ecos_bulk_mail_id_gen")
    var id: Long? = null,

    @get: NotNull
    @Column(columnDefinition = "VARCHAR(255)", unique = true, nullable = false)
    var extId: String? = null,

    var record: String? = null,

    var template: String? = null,

    @get: NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var type: NotificationType? = null,

    var title: String? = null,

    var body: String? = null,

    @get: NotNull
    @Column(nullable = false)
    var recipientsData: String? = null,

    var config: String? = null,

    @get: NotNull
    @Column(columnDefinition = "VARCHAR(50)", nullable = false)
    var status: String? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()
) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BulkMailEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return 31
    }
}
