package ru.citeck.ecos.notifications.domain.notification.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.citeck.ecos.notifications.domain.notification.NotificationState

interface NotificationRepository : JpaRepository<NotificationEntity, Long> {

    fun findAllByState(state: NotificationState): List<NotificationEntity>

}
