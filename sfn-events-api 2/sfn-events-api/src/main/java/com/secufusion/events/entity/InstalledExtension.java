package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a browser extension installed on a device.
 * Tracks extension metadata, permissions, status, and policy evaluation results.
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "installed_extensions", indexes = {
        @Index(name = "idx_installed_ext_device", columnList = "fk_device_id"),
        @Index(name = "idx_installed_ext_tenant", columnList = "tenant_id"),
        @Index(name = "idx_installed_ext_user", columnList = "user_id"),
        @Index(name = "idx_installed_ext_extension_id", columnList = "extension_id"),
        @Index(name = "idx_installed_ext_status", columnList = "status"),
        @Index(name = "idx_installed_ext_policy_action", columnList = "policy_action"),
        @Index(name = "idx_installed_ext_risk_level", columnList = "risk_level")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_extension", columnNames = {"fk_device_id", "extension_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstalledExtension extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_installed_extension_id", nullable = false, updatable = false)
    private String pkInstalledExtensionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_device_id", nullable = false)
    private Device device;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "user_id", length = 50)
    private String userId;

    // ===== Extension Identification =====

    /**
     * Chrome/Edge extension ID (e.g., "nkbihfbeogaeaoehlefnkodbefgpgknn")
     */
    @Column(name = "extension_id", nullable = false, length = 64)
    private String extensionId;

    /**
     * Display name of the extension
     */
    @Column(name = "extension_name", length = 255)
    private String extensionName;

    /**
     * Current installed version
     */
    @Column(name = "version", length = 50)
    private String version;

    /**
     * Extension description from manifest
     */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Extension homepage URL
     */
    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    /**
     * Chrome Web Store URL
     */
    @Column(name = "store_url", length = 500)
    private String storeUrl;

    /**
     * Extension icon URL
     */
    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    // ===== Permissions =====

    /**
     * List of permissions requested by the extension (stored as JSON array)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private List<String> permissions;

    /**
     * List of host permissions (URLs the extension can access)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "host_permissions", columnDefinition = "jsonb")
    private List<String> hostPermissions;

    /**
     * List of optional permissions
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optional_permissions", columnDefinition = "jsonb")
    private List<String> optionalPermissions;

    // ===== Status & Policy =====

    /**
     * Current status of the extension
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExtensionStatus status = ExtensionStatus.ACTIVE;

    /**
     * Policy action applied: ALLOW, BLOCK, WARN
     */
    @Column(name = "policy_action", length = 20)
    private String policyAction;

    /**
     * Reason for the policy action
     */
    @Column(name = "policy_reason", length = 500)
    private String policyReason;

    /**
     * ID of the policy rule that matched
     */
    @Column(name = "matched_policy_id", length = 50)
    private String matchedPolicyId;

    /**
     * Whether extension is whitelisted
     */
    @Column(name = "is_whitelisted")
    private Boolean isWhitelisted = false;

    /**
     * Whether extension is blacklisted
     */
    @Column(name = "is_blacklisted")
    private Boolean isBlacklisted = false;

    // ===== Risk Assessment =====

    /**
     * Calculated risk level: HIGH, MEDIUM, LOW, NONE
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /**
     * Risk score (0-100)
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * High-risk permissions found in this extension
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "high_risk_permissions", columnDefinition = "jsonb")
    private List<String> highRiskPermissions;

    // ===== Installation Info =====

    /**
     * How the extension was installed: NORMAL, ADMIN, DEVELOPMENT, SIDELOAD, COMPONENT
     */
    @Column(name = "install_type", length = 30)
    private String installType;

    /**
     * Whether the extension is managed by enterprise policy
     */
    @Column(name = "is_managed")
    private Boolean isManaged = false;

    /**
     * Whether the extension can be disabled by user
     */
    @Column(name = "may_disable")
    private Boolean mayDisable = true;

    /**
     * Whether the extension runs in offline mode
     */
    @Column(name = "offline_enabled")
    private Boolean offlineEnabled = false;

    // ===== Timestamps =====

    /**
     * When the extension was first detected on this device
     */
    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    /**
     * When the extension was last seen/synced
     */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /**
     * When the extension was installed (if known)
     */
    @Column(name = "installed_at")
    private LocalDateTime installedAt;

    /**
     * When the extension was uninstalled (if applicable)
     */
    @Column(name = "uninstalled_at")
    private LocalDateTime uninstalledAt;

    @PrePersist
    protected void onCreate() {
        if (firstSeenAt == null) {
            firstSeenAt = LocalDateTime.now();
        }
        if (lastSeenAt == null) {
            lastSeenAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ExtensionStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastSeenAt = LocalDateTime.now();
    }
}
