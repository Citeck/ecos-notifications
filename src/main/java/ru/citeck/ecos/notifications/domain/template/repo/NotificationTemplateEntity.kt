package ru.citeck.ecos.notifications.domain.template.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "notification_template")
class NotificationTemplateEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    var id: Long? = null,

    @Column(name = "ext_id")
    var extId: String? = null,

    var name: String? = null,

    @Column(name = "notification_title")
    var notificationTitle: String? = null,

    var model: String? = null,

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "template")
    @MapKey(name = "lang")
    var data: MutableMap<String, TemplateDataEntity> = mutableMapOf(),

    @Column(name = "multi_template_config")
    var multiTemplateConfig: String? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()
) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationTemplateEntity) return false

        return id != null && other.id != null && id == other.id
    }

    override fun hashCode() = 31

    override fun toString(): String {
        return "NotificationTemplate(id=$id, " +
            "extId=$extId, " +
            "name=$name, " +
            "notificationTitle=$notificationTitle, " +
            "data=$data)"
    }

    companion object {
        private const val serialVersionUID = 1L
    }


}
