package ru.citeck.ecos.notifications.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.notifications.service.EcosTemplateLoader

@Configuration
class FreemarkerConfig(
    private val ecosTemplateLoader: EcosTemplateLoader
) {

    @Bean("notificationFreemarkerEngine")
    fun freemarkerConfiguration(): freemarker.template.Configuration {
        val configuration = freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_29)
        configuration.defaultEncoding = "UTF-8"
        configuration.templateLoader = ecosTemplateLoader

        return configuration
    }

}
