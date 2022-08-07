package ru.citeck.ecos.notifications.domain.sender.api.records;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utils class for RecordsDao
 */
public class Utils {

    public static Map<String, String> ATTRIBUTES_MAP = Stream.of(
        new AbstractMap.SimpleImmutableEntry<>(RecordConstants.ATT_MODIFIED, "lastModifiedDate"),
        new AbstractMap.SimpleImmutableEntry<>(RecordConstants.ATT_MODIFIER, "lastModifiedBy"),
        new AbstractMap.SimpleImmutableEntry<>(RecordConstants.ATT_CREATED, "createdDate"),
        new AbstractMap.SimpleImmutableEntry<>(RecordConstants.ATT_CREATOR, "createdBy")
    ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    /**
     * Returns org.springframework.data.domain.Sort or null from RecordsQuery
     *
     * @param recordsQuery not null
     */
    public static Sort getSort(@NotNull RecordsQuery recordsQuery) {
        List<Sort.Order> sorts =
            recordsQuery.getSortBy()
                .stream()
                .map(sortBy -> {
                    String attribute = sortBy.getAttribute();
                    if (StringUtils.isNotBlank(attribute)) {
                        attribute = ATTRIBUTES_MAP.getOrDefault(attribute, attribute);
                        return Optional.of(sortBy.getAscending() ? Sort.Order.asc(attribute) : Sort.Order.desc(attribute));
                    }
                    return Optional.<Sort.Order>empty();
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return sorts.isEmpty() ? null : Sort.by(sorts);
    }
}
