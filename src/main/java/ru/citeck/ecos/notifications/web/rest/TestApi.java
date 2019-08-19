package ru.citeck.ecos.notifications.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.mapstruct.ap.internal.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.notifications.domain.subscribe.dto.SubscriberDTO;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test-get")
public class TestApi {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RecordsService recordsService;

    @GetMapping
    public Object get() {

        /*RecordsQuery query = new RecordsQuery();
        query.setSourceId("subscribers@local-alfresco|admin");
        query.setQuery();

        RecordsQueryResult<RecordRef> recordRefRecordsQueryResult = recordsService.queryRecords(query);*/

        RecordRef recordRef = RecordRef.create("subscribers", "local-alfresco|admin");


        SubscriberDTO subscribers = recordsService.getMeta(recordRef, SubscriberDTO.class);
        RecordMeta attributes = recordsService.getAttributes(recordRef, Collections.asSet("id", "subscribes[]?json"));


        JsonNode node = OBJECT_MAPPER.valueToTree(subscribers);

        return attributes;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }
}
