package ru.citeck.ecos.notifications.domain.type

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.notifications.config.records.EcosTypeInfo
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao

@Profile("!test")
@Component
class EcosTypesRepo(val syncRecordsDao: RemoteSyncRecordsDao<EcosTypeInfo>) : TypesRepo {

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return emptyList()
    }

    override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {

        if (RecordRef.isEmpty(typeRef)) {
            return null

        }
        val record = syncRecordsDao.getRecord(typeRef.id).orElse(null) ?: return null

        return TypeInfo.create {
            withId(record.id)
            withName(record.name)
            withDispNameTemplate(record.dispNameTemplate)
            withParentRef(record.parentRef)
            withNumTemplateRef(record.numTemplateRef)
            withModel(record.model)
        }
    }
}

