package ru.citeck.ecos.notifications.domain.file.repo

import ru.citeck.ecos.notifications.domain.AbstractAuditingEntity
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "files")
class FileEntity @JvmOverloads constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    var id: Long? = null,

    @Column(name = "ext_id", unique = true)
    var extId: String? = null,

    var data: ByteArray? = null,

    createdBy: String? = null,
    createdDate: Instant? = Instant.now(),
    lastModifiedBy: String? = null,
    lastModifiedDate: Instant? = Instant.now()
) : AbstractAuditingEntity(createdBy, createdDate, lastModifiedBy, lastModifiedDate), Serializable {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as FileEntity
        return id == that.id
    }

    override fun hashCode(): Int {
        return 31
    }

    override fun toString(): String {
        return "FileEntity(id=$id, extId=$extId"
    }
}
