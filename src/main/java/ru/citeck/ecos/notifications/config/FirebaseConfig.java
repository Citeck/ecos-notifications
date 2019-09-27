package ru.citeck.ecos.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
@Profile("!test")
public class FirebaseConfig {

    private final ApplicationProperties appProps;

    public FirebaseConfig(ApplicationProperties appProps) {
        this.appProps = appProps;
    }

    @PostConstruct
    public void initFirebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase App already initialized");
            return;
        }

        FirebaseOptions options = null;

        log.info("Init firebase app from credentials: " + appProps.getFirebase().getCredentialClassPath());

        try (InputStream credentials = new ClassPathResource(
            appProps.getFirebase().getCredentialClassPath()).getInputStream()) {
            options = getOptionsFromInputStream(credentials);
        } catch (IOException e) {
            log.info("Credentials from class path not found, trying to get from absolute path...");

            try (InputStream credentials = new FileInputStream(appProps.getFirebase().getCredentialClassPath())) {
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
            .setDatabaseUrl(appProps.getFirebase().getDataBaseUrl())
            .build();
    }

}
