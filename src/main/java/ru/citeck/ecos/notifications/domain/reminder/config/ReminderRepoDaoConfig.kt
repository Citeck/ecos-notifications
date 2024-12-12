package ru.citeck.ecos.notifications.domain.reminder.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.record.dao.RecordsDao

const val REMINDER_TYPE_ID = "reminder"
const val REMINDER_SOURCE_ID = "reminder"
const val REMINDER_REPO_SOURCE_ID = "$REMINDER_TYPE_ID-repo"

const val REMINDER_ATT_DEFERRED_BULK_MAILS = "deferredBulkMails"
const val REMINDER_ATT_THRESHOLD_DURATIONS = "reminderThresholdDurations"
const val REMINDER_ATT_RECIPIENTS = "recipients"
const val REMINDER_ATT_CERTIFICATES = "certificates"
const val REMINDER_ATT_ENABLED = "enabled"
const val REMINDER_ATT_REMINDER_TYPE = "reminderType"

@Configuration
class ReminderRepoDaoConfig {

    @Bean
    fun reminderRepoDao(
        dbDomainFactory: DbDomainFactory
    ): RecordsDao {

        val reminderTypeRef = ModelUtils.getTypeRef(REMINDER_TYPE_ID)

        return dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(REMINDER_REPO_SOURCE_ID)
                        withTypeRef(reminderTypeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_reminder")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data")
            .withPermsComponent(AdminPerms())
            .build()
    }

    private class AdminPerms : DbPermsComponent {

        override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {

            val isAdmin = authorities.contains(AuthRole.ADMIN)

            return object : DbRecordPerms {
                override fun hasAttReadPerms(name: String): Boolean {
                    return isAdmin
                }

                override fun hasAttWritePerms(name: String): Boolean {
                    return isAdmin
                }

                override fun hasReadPerms(): Boolean {
                    return isAdmin
                }

                override fun hasWritePerms(): Boolean {
                    return isAdmin
                }

                override fun getAdditionalPerms(): Set<String> {
                    return emptySet()
                }

                override fun getAuthoritiesWithReadPermission(): Set<String> {
                    return emptySet()
                }
            }
        }
    }
}
