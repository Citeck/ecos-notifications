package ru.citeck.ecos.notifications.domain.subscribe.repo;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Roman Makarskiy
 */
@Data
public class SubscriberId implements Serializable {

    public static final String SEPARATOR = "|";

    private String tenantId;
    private String username;

    @Override
    public String toString() {
        return tenantId + SEPARATOR + username;
    }
}
