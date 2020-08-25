package ru.citeck.ecos.notifications.domain.template.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "template_images")
class TemplateImageEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    var id: Long? = null,

    var name: String? = null,

    @ManyToOne
    @JoinColumn(name = "template_id")
    var template: NotificationTemplateEntity? = null,

    var data: ByteArray? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()
) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TemplateImageEntity
        return id == that.id
    }

    override fun hashCode(): Int {
        return 31
    }

    override fun toString(): String {
        return "TemplateImage(id=$id, name=$name)"
    }


}
