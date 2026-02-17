package it.govpay.maggioli.batch.config;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TimezoneConfig {

    private final BatchProperties batchProperties;

    public TimezoneConfig(BatchProperties batchProperties) {
        this.batchProperties = batchProperties;
    }

    @PostConstruct
    public void init() {
        TimeZone timeZone = TimeZone.getTimeZone(batchProperties.getTimeZone());
        TimeZone.setDefault(timeZone);
        log.info("Timezone di default impostato a: {}", timeZone.getID());
    }

    @Bean
    public ZoneId applicationZoneId() {
        return batchProperties.getZoneId();
    }
}
