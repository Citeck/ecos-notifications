package ru.citeck.ecos.notifications.freemarker.beans

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.config.lib.service.EcosConfigService
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean

private const val ID = "config"

@Component
class EcosConfigAccessor(
    private val ecosConfigService: EcosConfigService
) : InjectedFreemarkerBean {


    override fun getId(): String {
        return ID
    }

    fun get(key: String): DataValue {
        return ecosConfigService.getValue(key)
    }

    fun getOrDefault(key: String, defaultValue: Any): DataValue {
        val result = ecosConfigService.getValue(key)
        if (result.isNotNull()) {
            return result
        }
        return DataValue.create(defaultValue)
    }

    fun getNotNull(key: String): DataValue {
        val result = ecosConfigService.getValue(key)
        if (result.isNull()) {
            error("Config value for key '$key' is null")
        }
        return result
    }

}
