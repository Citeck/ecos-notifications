package ru.citeck.ecos.notifications.service.providers;

import java.util.List;

public interface NotificationProvider {

    void send(String title, String body, List<String> to);

}
