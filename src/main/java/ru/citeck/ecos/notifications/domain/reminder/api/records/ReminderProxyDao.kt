package ru.citeck.ecos.notifications.domain.reminder.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_REPO_SOURCE_ID
import ru.citeck.ecos.notifications.domain.reminder.config.REMINDER_SOURCE_ID
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy

@Component
class ReminderProxyDao() : RecordsDaoProxy(
    REMINDER_SOURCE_ID,
    REMINDER_REPO_SOURCE_ID
)
