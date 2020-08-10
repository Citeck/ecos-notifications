package ru.citeck.ecos.notifications.domain.subscribe.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionEntity;
import ru.citeck.ecos.notifications.domain.subscribe.repo.ActionRepository;

import java.util.Optional;

/**
 * @author Roman Makarskiy
 */
@Service
@Transactional
public class ActionService {

    private final ActionRepository actionRepository;

    public ActionService(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    public ActionEntity save(ActionEntity action) {
        return actionRepository.save(action);
    }

    @Transactional(readOnly = true)
    public Optional<ActionEntity> findById(Long id) {
        return actionRepository.findById(id);
    }

    public void deleteById(Long id) {
        actionRepository.deleteById(id);
    }

}
