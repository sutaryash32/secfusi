package com.secufusion.tenant.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson Configuration
 *
 * Configures Jackson ObjectMapper with:
 * - Date/time serialization (ISO-8601 format instead of timestamps)
 * - Graceful handling of empty beans (prevents crashes with Hibernate proxies)
 * - Single value as array support (for flexibility)
 * - Proper support for @JsonManagedReference/@JsonBackReference
 *
 * NOTE: Repositories use explicit JOIN FETCH to avoid lazy initialization issues
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                // Use ISO-8601 date format instead of timestamps
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Don't fail when serializing empty beans or Hibernate proxies
                .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Allow single values to be accepted as arrays
                .featuresToEnable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .build();
    }
}
