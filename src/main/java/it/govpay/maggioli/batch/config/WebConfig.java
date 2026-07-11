package it.govpay.maggioli.batch.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import it.govpay.common.utils.DateTimePatterns;
import it.govpay.common.utils.OffsetDateTimeDeserializer;
import it.govpay.common.utils.OffsetDateTimeSerializer;

@Configuration
public class WebConfig {

	@Value("${spring.jackson.time-zone:Europe/Rome}")
	private String timezone;

	@Bean
	public ObjectMapper objectMapper() {
		// Registra serializzatori/deserializzatori custom per OffsetDateTime
		SimpleModule dateModule = new SimpleModule();
		dateModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
		dateModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());

		// Jackson 3: ObjectMapper e' immutabile, la configurazione avviene tramite builder.
		return JsonMapper.builder()
			.defaultTimeZone(TimeZone.getTimeZone(timezone))
			.defaultDateFormat(new SimpleDateFormat(DateTimePatterns.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX))
			.addModule(dateModule)
			.enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
			.enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
			.enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();
	}
}
