package ru.citeck.ecos.notifications.freemarker

import org.springframework.stereotype.Component

@Component
class AppendBarInjectedBean : InjectedFreemarkerBean {

    override fun getId(): String {
        return "barService"
    }

    fun append(str : String): String {
        return str + "bar"
    }

}
