package ru.citeck.ecos.notifications.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

@Slf4j
@Service
public class FreemarkerTemplateEngineService {

    private final Configuration freemarkerCfg;

    public FreemarkerTemplateEngineService(@Qualifier("notificationFreemarkerEngine") Configuration freemarkerCfg) {
        this.freemarkerCfg = freemarkerCfg;
    }

    public String process(String templateKey, String templateRepresentation, Map<String, Object> model) {
        Template template;

        try {
            template = new Template(templateKey, templateRepresentation,
                freemarkerCfg);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed create freemarker template. Key: <%s>, Template:\n%s",
                templateKey, templateRepresentation), e);
        }

        String processedStr;

        try (StringWriter stringWriter = new StringWriter()) {
            template.process(model, stringWriter);
            processedStr = stringWriter.toString();
        } catch (TemplateException | IOException e) {
            throw new RuntimeException(String.format("Failed to process freemarker template. Key: <%s>, Template:\n%s",
                templateKey, templateRepresentation), e);
        }

        //TODO: why freemarker return double quotes?
        String removedDoubleQuotes = StringUtils.isNotBlank(processedStr) ? processedStr.replaceAll("\"\"", "\"") : "";

        log.debug("Processed template {}" +
            "\nmodel: {}" +
            "\n-------> from" +
            "\n{}" +
            "\n-------> to" +
            "\n{}" +
            "\nremovedDoubleQuotes:" +
            "\n{}", templateKey, model, templateRepresentation, processedStr, removedDoubleQuotes);

        return removedDoubleQuotes;
    }

}
