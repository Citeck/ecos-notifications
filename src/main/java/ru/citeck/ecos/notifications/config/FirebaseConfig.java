package ru.citeck.ecos.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    private final FirebaseProps firebaseProps;

    public FirebaseConfig(FirebaseProps firebaseProps) {
        this.firebaseProps = firebaseProps;
    }

    @PostConstruct
    public void initFirebaseApp() {
        FirebaseOptions options = null;
        try (InputStream credentials = new ClassPathResource(
            firebaseProps.getCredentialClassPath()).getInputStream()) {

            options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(credentials))
                .setDatabaseUrl(firebaseProps.getDataBaseUrl())
                .build();

        } catch (IOException e) {
            log.error("Failed to get credentials for firebase", e);
        }

        if (options != null) {
            FirebaseApp.initializeApp(options);
        }

    }

}
