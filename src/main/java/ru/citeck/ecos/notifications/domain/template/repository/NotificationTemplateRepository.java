package ru.citeck.ecos.notifications.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.citeck.ecos.notifications.domain.template.entity.NotificationTemplate;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long>,
    JpaSpecificationExecutor<NotificationTemplate> {

    Optional<NotificationTemplate> findOneByExtId(String extId);

}
