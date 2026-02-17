package it.govpay.maggioli.batch.config;

import it.govpay.common.batch.config.BatchJobProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "govpay.batch")
@Getter
@Setter
public class BatchProperties extends BatchJobProperties {

    private String cron = "0 0 2 * * ?";

    private int threadPoolSize = 5;

    private int chunkSize = 100;

    private int maxRetries = 5;

    private boolean enabled = true;

}
