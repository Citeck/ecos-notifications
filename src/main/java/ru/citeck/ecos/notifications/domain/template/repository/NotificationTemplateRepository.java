package ru.citeck.ecos.notifications.domain.template.repository;

import org.springframework.data.repository.CrudRepository;
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate;

import java.util.Optional;

public interface NotificationTemplateRepository extends CrudRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findOneByExtId(String extId);

}
