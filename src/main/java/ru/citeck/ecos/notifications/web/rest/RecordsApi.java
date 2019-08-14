package ru.citeck.ecos.notifications.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.rest.MutationBody;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.request.rest.RestHandler;

/**
 * @author Roman Makarskiy
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/records")
public class RecordsApi {
    private final RestHandler restQueryHandler;

    private final RecordsService recordsService;

    @PostMapping("/query")
    public Object query(@RequestBody QueryBody queryBody) {
        return restQueryHandler.queryRecords(queryBody);
    }

    @PostMapping("/delete")
    public ResponseEntity<RecordsDelResult> delete(@RequestBody RecordsDeletion request) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .body(recordsService.delete(request));
    }

    @PostMapping("/mutate")
    public ResponseEntity<?> mutate(@RequestBody MutationBody request) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .body(restQueryHandler.mutateRecords(request));
    }
}
