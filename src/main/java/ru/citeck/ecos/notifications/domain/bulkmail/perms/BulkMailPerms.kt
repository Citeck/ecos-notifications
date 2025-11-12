package ru.citeck.ecos.notifications.domain.bulkmail.perms

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.EcosPermissionsService
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import ru.citeck.ecos.webapp.lib.perms.RecordPermsContext
import ru.citeck.ecos.webapp.lib.perms.component.RecordAttsPermsComponent
import ru.citeck.ecos.webapp.lib.perms.component.RecordAttsPermsData
import ru.citeck.ecos.webapp.lib.perms.component.RecordPermsComponent
import ru.citeck.ecos.webapp.lib.perms.component.RecordPermsData

@Component
class BulkMailPerms(
    ecosPermissionsService: EcosPermissionsService
) {
    private val permsCalc = ecosPermissionsService.createCalculator()
        .withoutDefaultComponents()
        .addComponent(BulkMailArtifactPermsComponent())
        .build()

    fun getPerms(ref: EntityRef): RecordPerms {
        return permsCalc.getPermissions(ref)
    }

    fun checkWrite(ref: EntityRef) {
        val perms = getPerms(ref)
        if (!perms.hasWritePerms()) {
            throw IllegalAccessException("Access denied")
        }
    }
}

class BulkMailArtifactPermsComponent : RecordPermsComponent, RecordAttsPermsComponent {

    private val order = -300f

    companion object {
        private val allowedWriteAuth = setOf(AuthRole.SYSTEM, AuthRole.ADMIN)
    }

    override fun getRecordPerms(context: RecordPermsContext): RecordPermsData {
        return Perms(context)
    }

    override fun getOrder(): Float {
        return order
    }

    override fun getRecordAttsPerms(context: RecordPermsContext): RecordAttsPermsData {
        return Perms(context)
    }

    private class Perms(
        private val permsContext: RecordPermsContext
    ) : RecordPermsData, RecordAttsPermsData {

        override fun getAdditionalPerms(): Set<String> {
            return permsContext.getAssignablePerms()
        }

        override fun hasReadPerms(): Boolean {
            return permsContext.getAuthorities().any { it in allowedWriteAuth }
        }

        override fun hasWritePerms(): Boolean {
            return permsContext.getAuthorities().any { it in allowedWriteAuth }
        }

        override fun hasAttReadPerms(name: String): Boolean {
            return hasReadPerms()
        }

        override fun hasAttWritePerms(name: String): Boolean {
            return hasWritePerms()
        }
    }
}
