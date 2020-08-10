package ru.citeck.ecos.notifications.domain.template.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long>,
    JpaSpecificationExecutor<NotificationTemplateEntity> {

    Optional<NotificationTemplateEntity> findOneByExtId(String extId);

}
