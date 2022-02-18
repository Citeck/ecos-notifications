package ru.citeck.ecos.notifications.domain.bulkmail.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import java.io.Serializable
import java.time.Instant
import javax.persistence.*
import javax.validation.constraints.NotNull

@Entity
@Table(name = "bulk_mail_recipient")
class BulkMailRecipientEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_bulk_mail_recipient_id_gen")
    @SequenceGenerator(name = "ecos_bulk_mail_recipient_id_gen")
    var id: Long? = null,

    @get: NotNull
    @Column(columnDefinition = "VARCHAR(255)", unique = true, nullable = false)
    var extId: String? = null,

    @get: NotNull
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    var bulkMailRef: String? = null,

    @Column(columnDefinition = "VARCHAR(255)")
    var record: String? = null,

    @get: NotNull
    @Column(nullable = false)
    var address: String? = null,

    var name: String? = null,

    @ManyToOne(optional = false) @NotNull
    var bulkMail: BulkMailEntity? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()

) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BulkMailRecipientEntity) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return 31
    }
}
