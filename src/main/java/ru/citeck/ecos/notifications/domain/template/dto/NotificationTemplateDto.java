package ru.citeck.ecos.notifications.domain.template.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class NotificationTemplateDto {

    @NotNull
    private String id;

    private String title;

    private Map<String, TemplateDataDto> data = new HashMap<>();

}
