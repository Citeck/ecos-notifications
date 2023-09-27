package ru.citeck.ecos.notifications.domain.notification.converter

import mu.KotlinLogging
import org.apache.commons.lang.LocaleUtils
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.notifications.domain.notification.DEFAULT_LOCALE
import ru.citeck.ecos.notifications.domain.notification.NotificationState
import ru.citeck.ecos.notifications.domain.notification.service.NotificationDao
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.freemarker.FreemarkerTemplateEngineService
import ru.citeck.ecos.records2.RecordRef

@Component
class NotificationTemplateConverter(
    val notificationTemplateService: NotificationTemplateService,
    val notificationDao: NotificationDao,
    val freemarkerService: FreemarkerTemplateEngineService
) {

    private val log = KotlinLogging.logger {}

    fun convertToReadableNotification(notificationDbId: Long, templateRef: RecordRef): String {
        val template = notificationTemplateService.findById(templateRef.id)
        val notification = notificationDao.getById(notificationDbId)
        if (notification == null || !template.isPresent || notification.state != NotificationState.SENT) {
            return ""
        }

        val notificationData = notification.data?.let { String(it) }
        val notificationAttributeMap = Json.mapper.read(notificationData, mutableMapOf<String, Any>().javaClass)
        val notificationModelMap: Map<String, Any> = notificationAttributeMap?.get("model") as Map<String, Any>
        val langValue = notificationAttributeMap.get("lang")?.toString()
        val locale = if (!langValue.isNullOrBlank()) {
            LocaleUtils.toLocale(langValue)
        } else {
            DEFAULT_LOCALE
        }
        val templateModelMap: Map<String, String> = template.get().model ?: emptyMap()

        val finalModelMap = replaceValues(templateModelMap, notificationModelMap)
        return try {
            freemarkerService.process(templateRef.id, locale, finalModelMap)
        } catch (e: RuntimeException) {
            log.debug(e) { "Failed to process notification template \"${templateRef.id}\": " + e.message }
            ""
        }
    }

    private fun replaceValues(templateModelMap: Map<String, String>, notificationModelMap: Map<String, Any>): MutableMap<String, Any?> {
        val resultMap = mutableMapOf<String, Any?>()
        templateModelMap.forEach { key, value ->
            val newValue = notificationModelMap[value]
            resultMap[key] = newValue
        }
        return resultMap
    }
}
