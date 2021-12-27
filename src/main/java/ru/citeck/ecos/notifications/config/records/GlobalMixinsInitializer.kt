package ru.citeck.ecos.notifications.config.records

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.type.api.records.TypesMixin
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import javax.annotation.PostConstruct

@Profile("!test")
@Component
class GlobalMixinsInitializer(
    @Qualifier("remoteTypesSyncRecordsDao")
    val typesRecordsDao: AbstractRecordsDao,
    val modelServices: ModelServiceFactory
) {

    @PostConstruct
    fun init() {
        typesRecordsDao.addAttributesMixin(TypesMixin(modelServices))
    }
}
