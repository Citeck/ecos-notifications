package ru.citeck.ecos.notifications.domain.notification.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.toDto
import ru.citeck.ecos.notifications.domain.notification.converter.toEntity
import ru.citeck.ecos.notifications.domain.notification.dto.NotificationDto
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import javax.annotation.PostConstruct

@Service
@Transactional
class NotificationDao(
    private val notificationRepository: NotificationRepository,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory,
    private val workspaceService: WorkspaceService
) {

    private lateinit var searchConv: JpaSearchConverter<NotificationEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(NotificationEntity::class.java).build()
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun save(dto: NotificationDto): NotificationDto {
        return notificationRepository.save(dto.toEntity()).toDto()
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun saveAll(dto: List<NotificationDto>): List<NotificationDto> {
        return notificationRepository.saveAll(dto.map { it.toEntity() }).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun findAllEntitiesByState(notificationState: NotificationState, pageable: Pageable): List<NotificationDto> {
        return notificationRepository.findAllByState(notificationState, pageable).map { it.toDto() }.toList()
    }

    @Transactional(readOnly = true)
    fun findAllToDispatch(): List<NotificationDto> {
        return notificationRepository.findAllToDispatch().map { it.toDto() }.toList()
    }

    @Transactional(readOnly = true)
    fun getByExtId(extId: String): NotificationDto? {
        val found = notificationRepository.findOneByExtId(extId)

        return if (found.isPresent) {
            found.get().toDto()
        } else {
            null
        }
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): NotificationDto? {
        val found = notificationRepository.findById(id)

        return if (found.isPresent) {
            found.get().toDto()
        } else {
            null
        }
    }

    @Transactional(readOnly = true)
    fun getBulkMailStateSummary(bulkMailRef: String): Map<NotificationState, Long> {
        return notificationRepository.getNotificationStateSummaryForBulkMail(bulkMailRef)
            .associateBy({ NotificationState.valueOf(it.getState()) }, { it.getCount() })
    }

    @Transactional(readOnly = true)
    fun findByRecord(recordRef: String): List<NotificationDto> {
        return notificationRepository.findAllByRecord(recordRef).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun findNotificationForBulkMail(bulkMailRef: String): List<NotificationDto> {
        return notificationRepository.findAllByBulkMailRef(bulkMailRef).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun findNotificationForBulkMail(bulkMailRef: String, state: NotificationState): List<NotificationDto> {
        return notificationRepository.findByBulkMailRefAndStateIs(bulkMailRef, state).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getAll(
        max: Int,
        skip: Int,
        predicate: Predicate,
        workspaces: List<String>,
        sort: List<SortBy>
    ): List<NotificationDto> {
        val predicateWithWorkspaces = Predicates.and(
            workspaceService.buildAvailableWorkspacesPredicate(AuthContext.getCurrentUser(), workspaces),
            predicate
        )
        return searchConv.findAll(notificationRepository, predicateWithWorkspaces, max, skip, sort).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(): List<NotificationDto> {
        return notificationRepository.findAll().map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getAll(max: Int, skip: Int): List<NotificationDto> {
        val sorting = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sorting)

        return notificationRepository.findAll(page).map {
            it.toDto()
        }.toList()
    }

    @Transactional(readOnly = true)
    fun getCount(predicate: Predicate, workspaces: List<String>): Long {
        val predicateWithWorkspaces = Predicates.and(
            workspaceService.buildAvailableWorkspacesPredicate(AuthContext.getCurrentUser(), workspaces),
            predicate
        )
        return searchConv.getCount(notificationRepository, predicateWithWorkspaces)
    }

    private fun getCount(): Long {
        return notificationRepository.count()
    }
}
