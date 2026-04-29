package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Read-only entity mapping to the devices table in the shared database.
 * Represents a registered browser extension instance.
 * Used for dashboard statistics and reporting.
 */
@Entity
@Table(name = "devices")
@Immutable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserDevice {

    @Id
    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser_type", length = 100)
    private String browserType;

    @Column(name = "extension_version", length = 50)
    private String extensionVersion;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BrowserDeviceStatus status;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "os_info", length = 200)
    private String osInfo;

    @Column(name = "device_fingerprint", length = 100)
    private String deviceFingerprint;
}
