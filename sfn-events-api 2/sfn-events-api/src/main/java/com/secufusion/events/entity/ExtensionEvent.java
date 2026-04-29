package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Audit trail for extension-related events.
 * Records install, uninstall, update, block, and warning events.
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "extension_events", indexes = {
        @Index(name = "idx_ext_event_device", columnList = "fk_device_id"),
        @Index(name = "idx_ext_event_tenant", columnList = "tenant_id"),
        @Index(name = "idx_ext_event_user", columnList = "user_id"),
        @Index(name = "idx_ext_event_extension_id", columnList = "extension_id"),
        @Index(name = "idx_ext_event_type", columnList = "event_type"),
        @Index(name = "idx_ext_event_timestamp", columnList = "event_timestamp"),
        @Index(name = "idx_ext_event_policy_action", columnList = "policy_action"),
        @Index(name = "idx_ext_event_installed_ext", columnList = "fk_installed_extension_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionEvent extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_extension_event_id", nullable = false, updatable = false)
    private String pkExtensionEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_installed_extension_id")
    private InstalledExtension installedExtension;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    // ===== Extension Identification =====

    /**
     * Chrome/Edge extension ID
     */
    @Column(name = "extension_id", nullable = false, length = 64)
    private String extensionId;

    /**
     * Display name of the extension
     */
    @Column(name = "extension_name", length = 255)
    private String extensionName;

    /**
     * Version at the time of the event
     */
    @Column(name = "extension_version", length = 50)
    private String extensionVersion;

    // ===== Event Details =====

    /**
     * Type of extension event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ExtensionEventType eventType;

    /**
     * When the event occurred
     */
    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    /**
     * Human-readable description of the event
     */
    @Column(name = "event_description", length = 500)
    private String eventDescription;

    // ===== Version Change Details (for updates) =====

    /**
     * Previous version (for updates)
     */
    @Column(name = "previous_version", length = 50)
    private String previousVersion;

    /**
     * New version (for updates)
     */
    @Column(name = "new_version", length = 50)
    private String newVersion;

    // ===== Policy Details =====

    /**
     * Policy action that was applied: ALLOW, BLOCK, WARN
     */
    @Column(name = "policy_action", length = 20)
    private String policyAction;

    /**
     * Reason for the policy action
     */
    @Column(name = "policy_reason", length = 500)
    private String policyReason;

    /**
     * ID of the policy rule that triggered this action
     */
    @Column(name = "policy_rule_id", length = 50)
    private String policyRuleId;

    /**
     * Name of the policy
     */
    @Column(name = "policy_name", length = 200)
    private String policyName;

    // ===== Risk Details =====

    /**
     * Risk level at the time of the event
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /**
     * Risk score at the time of the event (0-100)
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * Whether the extension was on whitelist
     */
    @Column(name = "is_whitelisted")
    private Boolean isWhitelisted;

    /**
     * Whether the extension was on blacklist
     */
    @Column(name = "is_blacklisted")
    private Boolean isBlacklisted;

    // ===== User Action Details =====

    /**
     * User response to warning: ACKNOWLEDGED, DISMISSED, PROCEEDED, UNINSTALLED
     */
    @Column(name = "user_action", length = 30)
    private String userAction;

    /**
     * User's reason/comment (if provided)
     */
    @Column(name = "user_reason", length = 500)
    private String userReason;

    // ===== Device Context =====

    /**
     * IP address at the time of the event
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Browser type
     */
    @Column(name = "browser_type", length = 100)
    private String browserType;

    /**
     * Browser version
     */
    @Column(name = "browser_version", length = 50)
    private String browserVersion;

    // ===== Additional Data =====

    /**
     * Additional event details (JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @PrePersist
    protected void onCreate() {
        if (eventTimestamp == null) {
            eventTimestamp = LocalDateTime.now();
        }
    }
}
