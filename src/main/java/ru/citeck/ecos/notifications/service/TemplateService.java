
package ru.citeck.ecos.notifications.service;

import org.springframework.stereotype.Service;

//TODO: Implement a real templateService. In current implementation, its just a dummy.

@Service
public class TemplateService {

    private static final String UNI_TE_SUPERVISOR_FIREBASE_TEMPLATE = "unilever-te-supervisor-firebase-template";

    public String getTitleTemplate(String id) {
        //TODO get from template notification service
        if (UNI_TE_SUPERVISOR_FIREBASE_TEMPLATE.equals(id)) {
            /*return "<#assign package = customData.req.get('package')>\n" +
                "<#assign title = \"Пакет документов\"/>\n" +
                "<#if package?contains(\"ter-advance-report\")>\n" +
                "    <#assign title = \"Авансовый отчет\"/>\n" +
                "<#elseif package?contains(\"ter-travel-order\")>\n" +
                "    <#assign title = \"Приказ на командировку\"/>\n" +
                "</#if>\n" +
                "${title}";*/
            return "Test title";
        }

        return "";
    }

    public String getBodyTemplate(String id) {
        //TODO get from template notification service
        if (UNI_TE_SUPERVISOR_FIREBASE_TEMPLATE.equals(id)) {
            /*return "Требуется согласование. Заявка № ${customData.req.get('number')}. Сотрудник " +
                "${customData.req.get('employee')}.";*/
            return "Test body";
        }

        return "";
    }

}
