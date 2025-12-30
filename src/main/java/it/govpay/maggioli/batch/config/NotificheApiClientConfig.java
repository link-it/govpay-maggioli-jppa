package it.govpay.maggioli.batch.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for Notificge API client with authentication
 */
@Slf4j
@Configuration
public class NotificheApiClientConfig {

    private final BatchProperties batchProperties;

    public NotificheApiClientConfig(BatchProperties batchProperties) {
        this.batchProperties = batchProperties;
    }

    @Bean
    public RestTemplate notificheApiRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
            .rootUri(batchProperties.getServiceUrl())
//            .connectTimeout(Duration.ofMillis(batchProperties.getConnectionTimeout()))
//            .readTimeout(Duration.ofMillis(batchProperties.getReadTimeout()))
//            .additionalInterceptors(subscriptionKeyInterceptor(), responseBodyCapturingInterceptor())
            .build();

        // Note: BufferingClientHttpRequestFactory is NOT used here because it breaks the request pipeline.
        // The ResponseBodyCapturingInterceptor already handles buffering the response body for GDE logging.

        // Configure custom ObjectMapper for secure date handling from Notifiche API
        // Remove default Jackson converter and add our custom one
//        ObjectMapper objectMapper = createPagoPAObjectMapper();
//        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
//        restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
//        restTemplate.getMessageConverters().add(0, converter);

        return restTemplate;
    }

}
