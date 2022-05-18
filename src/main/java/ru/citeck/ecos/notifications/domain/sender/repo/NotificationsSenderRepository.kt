package ru.citeck.ecos.notifications.domain.sender.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.*

interface NotificationsSenderRepository : JpaRepository<NotificationsSenderEntity, Long>,
    JpaSpecificationExecutor<NotificationsSenderEntity> {

    fun findOneByExtId(extId: String): Optional<NotificationsSenderEntity>

    fun deleteAllByExtIdIn(extIds: List<String>)
}
