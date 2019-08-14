package ru.citeck.ecos.notifications.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * @author Roman Makarskiy
 */
@Configuration
public class TemplateConfig {

    @Bean("eventsTemplateEngine")
    public TemplateEngine templateEngine() {
        final SpringTemplateEngine templateEngine = new SpringTemplateEngine();

        final StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setOrder(1);
        templateResolver.setTemplateMode("HTML");
        templateResolver.setCacheable(false);

        templateEngine.addTemplateResolver(templateResolver);

        return templateEngine;
    }

}
