package ru.citeck.ecos.notifications.domain.notification.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState

interface FailureNotificationRepository : JpaRepository<FailureNotificationEntity, Long> {

    fun findAllByState(state: FailureNotificationState): List<FailureNotificationEntity>

}
