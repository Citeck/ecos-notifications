package ru.citeck.ecos.notifications.domain.template.repository;

import org.springframework.data.repository.CrudRepository;
import ru.citeck.ecos.notifications.domain.template.entity.TemplateData;

public interface TemplateDataRepository extends CrudRepository<TemplateData, Long> {
}
