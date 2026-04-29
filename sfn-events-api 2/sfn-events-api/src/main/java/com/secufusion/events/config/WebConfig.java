package com.secufusion.events.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.core.convert.converter.Converter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Register custom converter for LocalDateTime that handles ISO 8601 with timezone (Z suffix)
        registry.addConverter(new StringToLocalDateTimeConverter());

        // Register standard date/time formatters
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);
    }

    /**
     * Custom converter to handle ISO 8601 timestamps with timezone indicator (Z or +00:00).
     * Converts strings like "2026-01-19T07:21:37.459Z" to LocalDateTime.
     */
    public static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }

            String trimmed = source.trim();

            try {
                // First, try parsing as ISO instant (with Z or timezone offset)
                if (trimmed.endsWith("Z") || trimmed.contains("+") ||
                    (trimmed.lastIndexOf('-') > 10)) {
                    // Parse as Instant and convert to LocalDateTime in UTC
                    Instant instant = Instant.parse(trimmed);
                    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                }

                // Try standard LocalDateTime parsing (without timezone)
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            } catch (DateTimeParseException e) {
                // Fallback: try parsing with flexible ISO format
                try {
                    return LocalDateTime.parse(trimmed);
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException(
                        "Cannot parse date-time: " + source + ". Expected ISO 8601 format (e.g., 2026-01-19T07:21:37 or 2026-01-19T07:21:37.459Z)", ex);
                }
            }
        }
    }
}

