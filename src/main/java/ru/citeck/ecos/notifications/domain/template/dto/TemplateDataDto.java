package ru.citeck.ecos.notifications.domain.template.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class TemplateDataDto {

    private String name;

    private byte[] data;

}
