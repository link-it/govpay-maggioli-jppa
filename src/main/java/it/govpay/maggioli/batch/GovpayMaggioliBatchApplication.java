package it.govpay.maggioli.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import it.govpay.common.client.config.GovPayClientAutoConfiguration;

/**
 * Main application class for GovPay Maggioli JPPA Batch
 */
@SpringBootApplication
@EnableScheduling
@Import(GovPayClientAutoConfiguration.class)
@EntityScan(basePackages = {"it.govpay.maggioli.batch.entity", "it.govpay.common.client.entity"})
@EnableJpaRepositories(basePackages = "it.govpay.maggioli.batch.repository")
public class GovpayMaggioliBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovpayMaggioliBatchApplication.class, args);
    }
}
