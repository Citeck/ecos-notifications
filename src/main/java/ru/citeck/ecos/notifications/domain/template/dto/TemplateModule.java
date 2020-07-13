package ru.citeck.ecos.notifications.domain.template.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TemplateModule {

    private String id;
    private String title;
    private byte[] data;

    public TemplateModule() {
    }

}
