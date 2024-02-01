package ru.citeck.ecos.notifications.domain.notification.repo

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.citeck.ecos.notifications.domain.notification.FailureNotificationState

interface FailureNotificationRepository : JpaRepository<FailureNotificationEntity, Long> {

    fun findAllByState(state: FailureNotificationState): List<FailureNotificationEntity>

    fun findByStateOrderByCreatedDateDesc(state: FailureNotificationState, pageable: Pageable): List<FailureNotificationEntity>

}
