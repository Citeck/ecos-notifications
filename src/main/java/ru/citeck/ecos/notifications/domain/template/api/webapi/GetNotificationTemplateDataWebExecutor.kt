package ru.citeck.ecos.notifications.domain.template.api.webapi

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateAttsCalculator
import ru.citeck.ecos.notifications.lib.api.NotificationsAppWebApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp

@Component
class GetNotificationTemplateDataWebExecutor(
    private val notificationTemplateAttsCalculator: NotificationTemplateAttsCalculator
) : EcosWebExecutor {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {

        val req = request.getBodyReader()
            .readDto(NotificationsAppWebApi.GetTemplateWithRequiredAttsReq::class.java)

        val result = notificationTemplateAttsCalculator.getTemplateData(
            req.templateRef,
            req.attributes,
            req.contextData
        )

        log.debug { "Get template data webapi request: $req. Result: $result" }

        response.getBodyWriter().writeDto(result)
    }

    override fun getPath(): String {
        return NotificationsAppWebApi.GET_TEMPLATE_DATA_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
