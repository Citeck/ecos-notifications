package ru.citeck.ecos.notifications.domain.notification.repo

/**
 * @author Roman Makarskiy
 */
interface BulkNotificationStateSummaryProjection {

    fun getState(): String

    fun getCount(): Long
}
