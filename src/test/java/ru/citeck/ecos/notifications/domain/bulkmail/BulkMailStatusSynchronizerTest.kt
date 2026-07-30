package ru.citeck.ecos.notifications.domain.bulkmail

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.notifications.NotificationsApp
import ru.citeck.ecos.notifications.domain.bulkmail.dto.BulkMailDto
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailDao
import ru.citeck.ecos.notifications.domain.bulkmail.service.BulkMailStatusSynchronizer
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.converter.recordRef
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationEntity
import ru.citeck.ecos.notifications.domain.notification.repo.NotificationRepository
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * Verifies the [BulkMailStatusSynchronizer] priority chain over notification state summaries:
 * ERROR keeps retrying (TRYING_TO_DISPATCH), WAIT_FOR_DISPATCH keeps waiting, terminal
 * failures (EXPIRED/FAILED) flip the bulk mail to ERROR only when nothing is in flight,
 * otherwise everything settled means SENT.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [NotificationsApp::class])
class BulkMailStatusSynchronizerTest {

    @Autowired
    private lateinit var bulkMailDao: BulkMailDao

    @Autowired
    private lateinit var bulkMailStatusSynchronizer: BulkMailStatusSynchronizer

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @BeforeEach
    fun setUp() {
        notificationRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        notificationRepository.deleteAll()
    }

    private fun bulkMailInDispatch(): BulkMailDto {
        val bulkMail = bulkMailDao.save(
            BulkMailDto(
                id = null,
                type = NotificationType.EMAIL_NOTIFICATION
            )
        )
        bulkMailDao.setStatus(bulkMail.extId!!, BulkMailStatus.TRYING_TO_DISPATCH)
        return bulkMailDao.findByExtId(bulkMail.extId!!)!!
    }

    private fun addNotification(bulkMail: BulkMailDto, state: NotificationState) {
        notificationRepository.save(
            NotificationEntity(
                state = state,
                bulkMailRef = bulkMail.recordRef.toString()
            )
        )
    }

    private fun syncAndGetStatus(bulkMail: BulkMailDto): String {
        bulkMailStatusSynchronizer.sync()
        return bulkMailDao.findByExtId(bulkMail.extId!!)!!.status
    }

    @Test
    fun `error rows keep bulk mail in trying to dispatch`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.ERROR)
        addNotification(bulkMail, NotificationState.SENT)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.TRYING_TO_DISPATCH.status)
    }

    @Test
    fun `failed rows without in flight rows flip bulk mail to error`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.FAILED)
        addNotification(bulkMail, NotificationState.SENT)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.ERROR.status)
    }

    @Test
    fun `expired rows without in flight rows flip bulk mail to error`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.EXPIRED)
        addNotification(bulkMail, NotificationState.SENT)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.ERROR.status)
    }

    @Test
    fun `expired row must not flip bulk mail to error while other rows are still retrying`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.EXPIRED)
        addNotification(bulkMail, NotificationState.ERROR)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.TRYING_TO_DISPATCH.status)
    }

    @Test
    fun `expired row must not flip bulk mail to error while other rows are waiting for dispatch`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.EXPIRED)
        addNotification(bulkMail, NotificationState.WAIT_FOR_DISPATCH)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.WAIT_FOR_DISPATCH.status)
    }

    @Test
    fun `only sent and cancelled rows mean bulk mail is sent`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.SENT)
        addNotification(bulkMail, NotificationState.CANCELLED)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.SENT.status)
    }

    /**
     * A bulk mail deleted mid-flight cancels every pending and ERROR row: nothing is in flight
     * anymore and nothing failed terminally, so the bulk mail is settled.
     */
    @Test
    fun `all cancelled rows mean bulk mail is sent`() {
        val bulkMail = bulkMailInDispatch()
        addNotification(bulkMail, NotificationState.CANCELLED)
        addNotification(bulkMail, NotificationState.CANCELLED)

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.SENT.status)
    }

    /**
     * No notifications produced yet (recipients are still being calculated): the status must be
     * left untouched instead of being resolved from an empty summary.
     */
    @Test
    fun `bulk mail without notifications keeps its status`() {
        val bulkMail = bulkMailInDispatch()

        assertThat(syncAndGetStatus(bulkMail)).isEqualTo(BulkMailStatus.TRYING_TO_DISPATCH.status)
    }
}
