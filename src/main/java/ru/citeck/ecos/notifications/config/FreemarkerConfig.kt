package ru.citeck.ecos.notifications.config

import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.notifications.freemarker.EcosTemplateLoader
import ru.citeck.ecos.notifications.freemarker.InjectedFreemarkerBean

private val FREEMARKER_VERSION = freemarker.template.Configuration.VERSION_2_3_29

@Configuration
class FreemarkerConfig(
    private val ecosTemplateLoader: EcosTemplateLoader
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Bean("notificationFreemarkerEngine")
    fun freemarkerConfiguration(injectedBeans: List<InjectedFreemarkerBean>): freemarker.template.Configuration {
        val configuration = freemarker.template.Configuration(FREEMARKER_VERSION)
        configuration.defaultEncoding = "UTF-8"
        configuration.templateLoader = ecosTemplateLoader

        injectedBeans.forEach {
            log.info { "Inject freemarker bean: ${it.getId()}, ${it.javaClass}" }
            configuration.setSharedVariable(it.getId(), it)
        }

        return configuration
    }

}
