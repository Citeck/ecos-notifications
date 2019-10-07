package ru.citeck.ecos.notifications.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.notifications.domain.subscribe.Action;
import ru.citeck.ecos.notifications.repository.ActionRepository;

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

    public Action save(Action action) {
        return actionRepository.save(action);
    }

    @Transactional(readOnly = true)
    public Optional<Action> findById(Long id) {
        return actionRepository.findById(id);
    }

    public void deleteById(Long id) {
        actionRepository.deleteById(id);
    }

}
