package it.govpay.maggioli.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay Maggioli JPPA Batch
 */
@SpringBootApplication
@EnableScheduling
public class GovpayMaggioliBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovpayMaggioliBatchApplication.class, args);
    }
}
