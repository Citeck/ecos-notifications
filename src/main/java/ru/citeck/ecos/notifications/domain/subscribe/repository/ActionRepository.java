package ru.citeck.ecos.notifications.domain.subscribe.repository;

import org.springframework.data.repository.CrudRepository;
import ru.citeck.ecos.notifications.domain.subscribe.entity.Action;

/**
 * @author Roman Makarskiy
 */
public interface ActionRepository extends CrudRepository<Action, Long> {
}
