package it.govpay.maggioli.batch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for GovpayFdrBatchApplication
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.batch.job.enabled=false"})
class GovpayMaggioliBatchApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully
    }
}
