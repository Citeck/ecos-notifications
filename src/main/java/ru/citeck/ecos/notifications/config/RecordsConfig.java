package ru.citeck.ecos.notifications.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records2.source.dao.RecordsDAO;
import ru.citeck.ecos.records2.source.dao.remote.RecordsRestConnection;
import ru.citeck.ecos.records2.source.dao.remote.RemoteRecordsDAO;

import javax.net.ssl.*;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Configuration
public class RecordsConfig extends RecordsServiceFactory {

    private static final String ALFRESCO_SOURCE_ID = "alfresco";

    @Bean
    public RecordsService createRecordsService(List<RecordsDAO> recordsDao) {
        RecordsService recordsService = super.createRecordsService();
        recordsDao.forEach(recordsService::register);
        return recordsService;
    }

    @Bean
    public RemoteRecordsDAO createAlfrescoRecordsDao(@Qualifier("alfrescoRestTemplate")
                                                         RestTemplate alfrescoRestTemplate) {
        RemoteRecordsDAO alfrescoRemote = new RemoteRecordsDAO();
        alfrescoRemote.setRecordsMethod("/alfresco/service/citeck/ecos/records/query");
        alfrescoRemote.setId(ALFRESCO_SOURCE_ID);
        alfrescoRemote.setRestConnection(new RecordsRestConnection() {
            @Override
            public <T> T jsonPost(String s, Object o, Class<T> aClass) {
                return alfrescoRestTemplate.postForObject(s, o, aClass);
            }
        });
        return alfrescoRemote;
    }

    @Bean
    public PredicateService createPredicateService() {
        return super.createPredicateService();
    }

    @Bean
    public RestHandler createRestHandler(RecordsService recordsService) {
        return new RestHandler(recordsService);
    }

    @Bean({"alfrescoRestTemplate"})
    RestTemplate initAlfrescoRestTemplate(RestTemplateBuilder restTemplateBuilder,
                                          ApplicationProperties properties) {

        BasicAuthorizationInterceptor authInterceptor = new BasicAuthorizationInterceptor(
            properties.getAlfresco().getAuthentication().getUsername(),
            properties.getAlfresco().getAuthentication().getPassword()
        );

        return restTemplateBuilder
            .requestFactory(SkipSslVerificationHttpRequestFactory.class)
            .additionalInterceptors(authInterceptor)
            .rootUri(properties.getAlfresco().getURL())
            .setConnectTimeout(properties.getAlfresco().getConnectionTimeout())
            .setReadTimeout(properties.getAlfresco().getReadTimeout())
            .build();
    }

    //Basically copied from org.springframework.boot.actuate.autoconfigure.cloudfoundry.servlet.SkipSslVerificationHttpRequestFactory
    private static class SkipSslVerificationHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, @NotNull String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection) {
                try {
                    ((HttpsURLConnection) connection).setHostnameVerifier(
                        (String s, SSLSession sslSession) -> true);
                    ((HttpsURLConnection) connection).setSSLSocketFactory(
                        this.createSslSocketFactory());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            super.prepareConnection(connection, httpMethod);
        }

        private SSLSocketFactory createSslSocketFactory() throws Exception {
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new SkipX509TrustManager()}, new SecureRandom());
            return context.getSocketFactory();
        }

        private static class SkipX509TrustManager implements X509TrustManager {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
        }
    }

}
