package ru.citeck.ecos.notifications.repository;

import org.springframework.data.repository.CrudRepository;
import ru.citeck.ecos.notifications.domain.subscribe.Action;

/**
 * @author Roman Makarskiy
 */
public interface ActionRepository extends CrudRepository<Action, Long> {
}
