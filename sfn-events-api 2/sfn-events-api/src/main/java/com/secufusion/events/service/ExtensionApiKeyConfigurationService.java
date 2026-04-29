package com.secufusion.events.service;

import com.secufusion.events.entity.ExtensionApiKeyConfiguration;
import com.secufusion.events.repository.ExtensionApiKeyConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * ExtensionApiKeyConfigurationService
 *
 * Manages API key configuration and policy settings.
 * Provides default values and allows runtime configuration updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionApiKeyConfigurationService {

    private final ExtensionApiKeyConfigurationRepository configRepository;

    /**
     * Initialize default configurations on application startup
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultConfigurations() {
        log.info("[API-KEY-CONFIG] Initializing default configurations");

        for (ExtensionApiKeyConfiguration.ConfigKey configKey : ExtensionApiKeyConfiguration.ConfigKey.values()) {
            if (!configRepository.existsByConfigKey(configKey.name())) {
                ExtensionApiKeyConfiguration config = ExtensionApiKeyConfiguration.builder()
                    .configKey(configKey.name())
                    .configValue(configKey.getDefaultValue())
                    .description(getConfigDescription(configKey))
                    .isEditable(true)
                    .build();

                configRepository.save(config);
                log.info("[API-KEY-CONFIG] Initialized config: {} = {}",
                    configKey.name(), configKey.getDefaultValue());
            }
        }
    }

    /**
     * Get configuration value as String
     */
    @Transactional(readOnly = true)
    public String getConfigValue(String configKey, String defaultValue) {
        return configRepository.findByConfigKey(configKey)
            .map(ExtensionApiKeyConfiguration::getConfigValue)
            .orElse(defaultValue);
    }

    /**
     * Get configuration value as Integer
     */
    @Transactional(readOnly = true)
    public Integer getConfigValueAsInt(String configKey, Integer defaultValue) {
        try {
            String value = getConfigValue(configKey, null);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("[API-KEY-CONFIG] Invalid integer config value for {}, using default", configKey);
            return defaultValue;
        }
    }

    /**
     * Get configuration value as Boolean
     */
    @Transactional(readOnly = true)
    public Boolean getConfigValueAsBoolean(String configKey, Boolean defaultValue) {
        String value = getConfigValue(configKey, null);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * Get all configurations as a map
     */
    @Transactional(readOnly = true)
    public Map<String, String> getAllConfigurations() {
        Map<String, String> configs = new HashMap<>();
        configRepository.findAll().forEach(config ->
            configs.put(config.getConfigKey(), config.getConfigValue())
        );
        return configs;
    }

    /**
     * Update configuration value
     */
    @Transactional
    public void updateConfiguration(String configKey, String configValue, String updatedBy) {
        ExtensionApiKeyConfiguration config = configRepository.findByConfigKey(configKey)
            .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + configKey));

        if (!config.getIsEditable()) {
            throw new IllegalStateException("Configuration is not editable: " + configKey);
        }

        config.setConfigValue(configValue);
        config.setUpdatedBy(updatedBy);
        configRepository.save(config);

        log.info("[API-KEY-CONFIG] Updated config: {} = {} by {}", configKey, configValue, updatedBy);
    }

    /**
     * Update multiple configurations
     */
    @Transactional
    public void updateConfigurations(Map<String, String> configs, String updatedBy) {
        configs.forEach((key, value) -> updateConfiguration(key, value, updatedBy));
    }

    /**
     * Get default expiry days
     */
    public Integer getDefaultExpiryDays() {
        return getConfigValueAsInt(
            ExtensionApiKeyConfiguration.ConfigKey.DEFAULT_EXPIRY_DAYS.name(),
            90
        );
    }

    /**
     * Get expiry warning threshold days
     */
    public Integer getExpiryWarningThresholdDays() {
        return getConfigValueAsInt(
            ExtensionApiKeyConfiguration.ConfigKey.EXPIRY_WARNING_THRESHOLD_DAYS.name(),
            7
        );
    }

    /**
     * Check if auto-disable after expiry is enabled
     */
    public Boolean isAutoDisableAfterExpiry() {
        return getConfigValueAsBoolean(
            ExtensionApiKeyConfiguration.ConfigKey.AUTO_DISABLE_AFTER_EXPIRY.name(),
            true
        );
    }

    /**
     * Check if expiry extension is allowed
     */
    public Boolean isExpiryExtensionAllowed() {
        return getConfigValueAsBoolean(
            ExtensionApiKeyConfiguration.ConfigKey.ALLOW_EXPIRY_EXTENSION.name(),
            false
        );
    }

    /**
     * Get maximum expiry extension days
     */
    public Integer getMaxExpiryExtensionDays() {
        return getConfigValueAsInt(
            ExtensionApiKeyConfiguration.ConfigKey.MAX_EXPIRY_EXTENSION_DAYS.name(),
            365
        );
    }

    /**
     * Get maximum keys per tenant (0 = unlimited)
     */
    public Integer getMaxKeysPerTenant() {
        return getConfigValueAsInt(
            ExtensionApiKeyConfiguration.ConfigKey.MAX_KEYS_PER_TENANT.name(),
            0
        );
    }

    /**
     * Get default rate limit (0 = unlimited)
     */
    public Integer getDefaultRateLimit() {
        return getConfigValueAsInt(
            ExtensionApiKeyConfiguration.ConfigKey.DEFAULT_RATE_LIMIT.name(),
            0
        );
    }

    /**
     * Get configuration description
     */
    private String getConfigDescription(ExtensionApiKeyConfiguration.ConfigKey configKey) {
        return switch (configKey) {
            case DEFAULT_EXPIRY_DAYS -> "Default expiry period for new API keys (in days)";
            case EXPIRY_WARNING_THRESHOLD_DAYS -> "Days before expiry to show warning messages";
            case AUTO_DISABLE_AFTER_EXPIRY -> "Automatically disable keys after expiry";
            case ALLOW_EXPIRY_EXTENSION -> "Allow extending expiry dates for keys";
            case MAX_EXPIRY_EXTENSION_DAYS -> "Maximum days a key can be extended";
            case MAX_KEYS_PER_TENANT -> "Maximum active keys per tenant (0 = unlimited)";
            case DEFAULT_RATE_LIMIT -> "Default rate limit per key (0 = unlimited)";
        };
    }
}
