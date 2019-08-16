package ru.citeck.ecos.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
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
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase App already initialized");
            return;
        }

        FirebaseOptions options = null;

        log.info("Init firebase app from credentials: " + firebaseProps.getCredentialClassPath());

        try (InputStream credentials = new ClassPathResource(
            firebaseProps.getCredentialClassPath()).getInputStream()) {
            options = getOptionsFromInputStream(credentials);
        } catch (IOException e) {
            log.info("Credentials from class path not found, trying to get from absolute path...");

            try (InputStream credentials = new FileInputStream(firebaseProps.getCredentialClassPath())) {
                options = getOptionsFromInputStream(credentials);
            } catch (IOException ex) {
                log.error("Failed to get credentials for firebase", e);
            }
        }

        if (options != null) {
            log.info("Firebase App options is found");
            FirebaseApp.initializeApp(options);
        } else {
            log.warn("Firebase App options not found");
        }
    }

    private FirebaseOptions getOptionsFromInputStream(InputStream credentials) throws IOException {
        return new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(credentials))
            .setDatabaseUrl(firebaseProps.getDataBaseUrl())
            .build();
    }

}
