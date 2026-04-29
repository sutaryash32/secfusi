package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * ExtensionApiKeyConfiguration Entity
 *
 * Stores configurable policies for API key management.
 * Provides system-wide settings for expiry, limits, and behavior.
 */
@Entity
@Table(name = "api_key_configuration", uniqueConstraints = {
    @UniqueConstraint(name = "uk_config_key", columnNames = {"config_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionApiKeyConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_config_id", length = 36, nullable = false, updatable = false)
    private String pkConfigId;

    /**
     * Configuration key (e.g., DEFAULT_EXPIRY_DAYS)
     */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * Configuration value (stored as string, parse as needed)
     */
    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    /**
     * Description of the configuration
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Whether this config is editable by admins
     */
    @Column(name = "is_editable", nullable = false)
    @Builder.Default
    private Boolean isEditable = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Configuration Keys Enum
     */
    public enum ConfigKey {
        DEFAULT_EXPIRY_DAYS("90"),
        EXPIRY_WARNING_THRESHOLD_DAYS("7"),
        AUTO_DISABLE_AFTER_EXPIRY("true"),
        ALLOW_EXPIRY_EXTENSION("false"),
        MAX_EXPIRY_EXTENSION_DAYS("365"),
        MAX_KEYS_PER_TENANT("0"), // 0 = unlimited
        DEFAULT_RATE_LIMIT("0"); // 0 = unlimited

        private final String defaultValue;

        ConfigKey(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }
}
