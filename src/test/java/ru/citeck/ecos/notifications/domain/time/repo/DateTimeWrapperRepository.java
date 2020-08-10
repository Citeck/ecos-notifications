package ru.citeck.ecos.notifications.domain.time.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.notifications.domain.time.entity.DateTimeWrapper;

/**
 * Spring Data JPA repository for the DateTimeWrapper entity.
 */
@Repository
public interface DateTimeWrapperRepository extends JpaRepository<DateTimeWrapper, Long> {

}
