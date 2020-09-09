package ru.citeck.ecos.notifications.domain.file.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long>,
    JpaSpecificationExecutor<FileEntity> {

    Optional<FileEntity> findOneByExtId(String extId);

}
