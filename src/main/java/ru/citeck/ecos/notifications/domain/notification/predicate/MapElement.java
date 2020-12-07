package ru.citeck.ecos.notifications.domain.notification.predicate;

import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.predicate.element.Element;
import ru.citeck.ecos.records2.predicate.element.elematts.ElementAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapElement implements Element {

    private final Map<String, Object> elements;

    public MapElement(Map<String, Object> elements) {
        this.elements = elements;
    }

    @NotNull
    @Override
    public ElementAttributes getAttributes(List<String> list) {
        Map<String, Object> newElements = new HashMap<>();
        list.forEach(elementKey -> {
            Object att = elements.get(elementKey);
            newElements.put(elementKey, att);
        });

        return new MapElements(newElements);
    }

    private static class MapElements implements ElementAttributes {

        private final Map<String, Object> elements;

        MapElements(Map<String, Object> elements) {
            this.elements = elements;
        }

        @Override
        public Object getAttribute(@NotNull String name) {
            return elements != null ? elements.get(name) : null;
        }
    }
}
