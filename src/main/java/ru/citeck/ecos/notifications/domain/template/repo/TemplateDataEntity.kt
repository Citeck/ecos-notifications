package ru.citeck.ecos.notifications.domain.template.repo

import jakarta.persistence.*
import jakarta.validation.constraints.Size
import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "template_data")
class TemplateDataEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "sequenceGenerator")
    var id: Long? = null,

    var name: String? = null,

    var lang:
    @Size(min = 2)
    String? = null,

    @ManyToOne
    @JoinColumn(name = "template_id")
    var template: NotificationTemplateEntity? = null,

    var data: ByteArray? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()
) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TemplateDataEntity
        return id == that.id
    }

    override fun hashCode(): Int {
        return 31
    }

    override fun toString(): String {
        return "TemplateData(id=$id, name=$name, lang=$lang)"
    }
}
