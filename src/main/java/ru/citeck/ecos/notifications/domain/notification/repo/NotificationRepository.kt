package ru.citeck.ecos.notifications.domain.notification.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import java.util.*

interface NotificationRepository : JpaRepository<NotificationEntity, Long>,
    JpaSpecificationExecutor<NotificationEntity> {

    fun findAllByState(state: NotificationState): List<NotificationEntity>

    fun findOneByExtId(extId: String): Optional<NotificationEntity>

}
