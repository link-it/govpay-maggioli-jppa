package it.govpay.maggioli.batch.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.common.utils.DateTimePatterns;
import it.govpay.common.utils.OffsetDateTimeDeserializer;
import it.govpay.common.utils.OffsetDateTimeSerializer;

@Configuration
public class WebConfig {

	@Value("${spring.jackson.time-zone:Europe/Rome}")
	private String timezone;

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.setTimeZone(TimeZone.getTimeZone(timezone));

		objectMapper.setDateFormat(
			new SimpleDateFormat(DateTimePatterns.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX)
		);

		objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);

		objectMapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		JavaTimeModule javaTimeModule = new JavaTimeModule();
		javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
		javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
		objectMapper.registerModule(javaTimeModule);

		return objectMapper;
	}
}
