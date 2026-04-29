package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * ExtensionApiKey Entity
 *
 * Stores API keys for browser extension authentication.
 * Uses SHA-256 hashing for secure key storage.
 * Integrates with Keycloak for JWT token generation.
 */
@Entity
@Table(name = "extension_api_keys", indexes = {
    @Index(name = "idx_api_key_hash", columnList = "key_hash"),
    @Index(name = "idx_api_key_tenant", columnList = "tenant_id"),
    @Index(name = "idx_api_key_status", columnList = "status"),
    @Index(name = "idx_api_key_expires", columnList = "expires_at"),
    @Index(name = "idx_api_key_prefix", columnList = "key_prefix")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_api_key_hash", columnNames = {"key_hash"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_extension_api_key_id", length = 36, nullable = false, updatable = false)
    private String pkExtensionApiKeyId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    /**
     * SHA-256 hash of the raw API key
     * Never store the raw key in the database
     */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * First 12 characters of the raw key for display purposes
     * Format: "sk_xxxxxxxxxx..."
     */
    @Column(name = "key_prefix", nullable = false, length = 12)
    private String keyPrefix;

    /**
     * Keycloak client ID for token generation
     */
    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    /**
     * Encrypted Keycloak client secret for token generation
     */
    @Column(name = "client_secret", nullable = false, length = 500)
    private String clientSecret;

    /**
     * User-friendly name for the API key
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Optional description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Status: ACTIVE, INACTIVE, REVOKED, EXPIRED, ROTATED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Expiry date (nullable for non-expiring keys)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Last time the key was used for validation/token generation
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Foreign key relationship to Tenant
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", referencedColumnName = "tenantID", insertable = false, updatable = false)
    private Tenant tenant;

    /**
     * Check if the key is expired
     */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the key is expiring soon (within threshold days)
     */
    @Transient
    public boolean isExpiringSoon(int thresholdDays) {
        if (expiresAt == null) return false;
        return LocalDateTime.now().plusDays(thresholdDays).isAfter(expiresAt)
            && !isExpired();
    }

    /**
     * Get days remaining until expiry
     */
    @Transient
    public Long getDaysRemaining() {
        if (expiresAt == null) return null;
        long days = java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays();
        return Math.max(0, days);
    }
}
