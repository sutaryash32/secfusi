package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity tracking user-device login relationships.
 * Maintains a record of all devices a user has logged in from,
 * along with trust status and login history.
 */
@Entity
@Table(name = "user_device_login", indexes = {
        @Index(name = "idx_udl_tenant", columnList = "tenant_id"),
        @Index(name = "idx_udl_user", columnList = "user_id"),
        @Index(name = "idx_udl_device", columnList = "device_id"),
        @Index(name = "idx_udl_fingerprint", columnList = "device_fingerprint"),
        @Index(name = "idx_udl_last_login", columnList = "last_login_at"),
        @Index(name = "idx_udl_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_udl_user_device", columnNames = {"user_id", "device_fingerprint", "tenant_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "device_fingerprint", nullable = false, length = 100)
    private String deviceFingerprint;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser_type", length = 100)
    private String browserType;

    @Column(name = "os_info", length = 100)
    private String osInfo;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "first_login_at", nullable = false)
    private LocalDateTime firstLoginAt;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Column(name = "last_ip_address", length = 45)
    private String lastIpAddress;

    @Column(name = "last_location", length = 200)
    private String lastLocation;

    @Column(name = "login_count", nullable = false)
    private Integer loginCount;

    @Column(name = "failed_login_count")
    private Integer failedLoginCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceLoginStatus status;

    @Column(name = "is_trusted")
    private Boolean isTrusted;

    @Column(name = "trusted_at")
    private LocalDateTime trustedAt;

    @Column(name = "trusted_by", length = 100)
    private String trustedBy;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_by", length = 100)
    private String blockedBy;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    @Column(name = "last_session_id", length = 100)
    private String lastSessionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (firstLoginAt == null) {
            firstLoginAt = LocalDateTime.now();
        }
        if (lastLoginAt == null) {
            lastLoginAt = LocalDateTime.now();
        }
        if (loginCount == null) {
            loginCount = 1;
        }
        if (failedLoginCount == null) {
            failedLoginCount = 0;
        }
        if (status == null) {
            status = DeviceLoginStatus.ACTIVE;
        }
        if (isTrusted == null) {
            isTrusted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Device login status enum.
     */
    public enum DeviceLoginStatus {
        ACTIVE,         // Device is active and can be used for login
        INACTIVE,       // Device hasn't been used recently
        BLOCKED,        // Device is blocked from login
        PENDING_VERIFICATION,  // New device pending user verification
        REVOKED         // Device access has been revoked
    }
}
