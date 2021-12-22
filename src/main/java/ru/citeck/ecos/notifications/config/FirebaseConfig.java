package ru.citeck.ecos.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import ecos.com.fasterxml.jackson210.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import ru.citeck.ecos.commons.json.Json;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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

        FirebaseOptions options = getOptionsFromAppProps();
        if (options == null) {
            options = getOptionsFromClassPath(appProps.getFirebase().getCredentialClassPath());
        }

        if (options != null) {
            log.info("Firebase App options is found");
            FirebaseApp.initializeApp(options);
        } else {
            log.warn("Firebase App options not found");
        }
    }

    private FirebaseOptions getOptionsFromAppProps() {
        ApplicationProperties.Credentials credentials = appProps.getFirebase().getCredentials();

        if (StringUtils.isBlank(credentials.getPrivateKeyId())) {
            return null;
        }

        log.info("Init firebase app from credentials config");
        Map<String, Object> credentialsData = new HashMap<>();
        credentialsData.put("type", credentials.getType());
        credentialsData.put("project_id", credentials.getProjectId());
        credentialsData.put("private_key_id", credentials.getPrivateKeyId());
        credentialsData.put("private_key", credentials.getPrivateKey());
        credentialsData.put("client_email", credentials.getClientEmail());
        credentialsData.put("client_id", credentials.getClientId());
        credentialsData.put("auth_uri", credentials.getAuthUri());
        credentialsData.put("token_uri", credentials.getTokenUri());
        credentialsData.put("auth_provider_x509_cert_url", credentials.getAuthProviderX509CertUrl());
        credentialsData.put("client_x509_cert_url", credentials.getClientX509CertUrl());

        try {
            JsonNode jsonNode = Json.getMapper().toJson(credentialsData);
            byte[] bytes = jsonNode.toString().replaceAll("\\\\n", "\\n").getBytes();

            return getOptionsFromInputStream(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.error("Credentials reading error", e);
        }

        return null;
    }

    @Nullable
    private FirebaseOptions getOptionsFromClassPath(String credentialsClassPath) {

        if (StringUtils.isBlank(credentialsClassPath)) {
            return null;
        }

        log.info("Init firebase app from credentials classpath: " + credentialsClassPath);

        FirebaseOptions options = null;

        try (InputStream credentials = new ClassPathResource(credentialsClassPath).getInputStream()) {
            options = getOptionsFromInputStream(credentials);
        } catch (IOException e) {
            log.info("Credentials from class path not found, trying to get from absolute path...");

            try (InputStream credentials = new FileInputStream(appProps.getFirebase().getCredentialClassPath())) {
                options = getOptionsFromInputStream(credentials);
            } catch (IOException ex) {
                log.error("Failed to get credentials for firebase", e);
            }
        }

        return options;
    }

    private FirebaseOptions getOptionsFromInputStream(InputStream credentials) throws IOException {
        return new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(credentials))
            .setDatabaseUrl(appProps.getFirebase().getDataBaseUrl())
            .build();
    }

}
