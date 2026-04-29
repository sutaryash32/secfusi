package com.secufusion.events.repository;

import com.secufusion.events.entity.ExtensionApiKeyConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ExtensionApiKeyConfigurationRepository
 *
 * Repository for managing API key configuration settings.
 */
@Repository
public interface ExtensionApiKeyConfigurationRepository extends JpaRepository<ExtensionApiKeyConfiguration, String> {

    /**
     * Find configuration by key
     */
    Optional<ExtensionApiKeyConfiguration> findByConfigKey(String configKey);

    /**
     * Check if configuration exists
     */
    boolean existsByConfigKey(String configKey);
}
