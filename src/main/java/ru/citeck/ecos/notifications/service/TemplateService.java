
package ru.citeck.ecos.notifications.service;

import org.springframework.stereotype.Service;

//TODO: Implement a real templateService. In current implementation, its just a dummy.

@Service
public class TemplateService {

    private static final String UNI_TE_SUPERVISOR_FIREBASE_TEMPLATE = "unilever-te-supervisor-firebase-template";

    public String getTitleTemplate (String id) {
        if (UNI_TE_SUPERVISOR_FIREBASE_TEMPLATE.equals(id)) {
            return "some-title";
        }

        return "";
    }

    public String getBodyTemplate(String id) {
        if (UNI_TE_SUPERVISOR_FIREBASE_TEMPLATE.equals(id)) {
            //return "[(${customData.req.get('number')})], [(${customData.req.get('employee')})], [(${customData.req.get('package').contains('workspace://SpacesStore/ter-internal-write-off-act')})]";
            return "[(${#arrays.contains(${#arrays.toArray(customData.req.get('package')), 'workspace://SpacesStore/ter-internal-write-off-act')})]";
        }

        return "";
    }

}
