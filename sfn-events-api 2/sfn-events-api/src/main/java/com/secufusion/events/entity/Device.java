package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * Represents a registered device (browser extension instance).
 * Tracks device metadata, ownership, and status.
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "devices", indexes = {
        @Index(name = "idx_device_tenant", columnList = "tenant_id"),
        @Index(name = "idx_device_status", columnList = "status"),
        @Index(name = "idx_device_last_seen", columnList = "last_seen_at"),
        @Index(name = "idx_device_fingerprint", columnList = "device_fingerprint, tenant_id"),
        @Index(name = "idx_device_device_user", columnList = "fk_device_user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "device_id", nullable = false, updatable = false)
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
    private DeviceStatus status;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "os_info", length = 200)
    private String osInfo;

    @Column(name = "device_fingerprint", length = 100)
    private String deviceFingerprint;

    // ===== Extension Tracking Fields (NEW) =====

    /**
     * Whether this device was registered anonymously (before user login)
     */
    @Column(name = "is_anonymous")
    private Boolean isAnonymous = false;

    /**
     * Unique token for anonymous device identification.
     * Used by browser extension to sync before user authentication.
     */
    @Column(name = "device_token", length = 64, unique = true)
    private String deviceToken;

    /**
     * When the anonymous device was linked to a user account
     */
    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    /**
     * User ID that this anonymous device was linked to
     */
    @Column(name = "linked_user_id", length = 50)
    private String linkedUserId;

    // ===== Device User Link (auto-populated by database trigger) =====

    /**
     * Foreign key to device_user table.
     * Auto-populated by database trigger on INSERT.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_device_user_id")
    private DeviceUser deviceUser;

    @PrePersist
    protected void onCreate() {
        if (firstSeenAt == null) {
            firstSeenAt = LocalDateTime.now();
        }
        if (lastSeenAt == null) {
            lastSeenAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DeviceStatus.ACTIVE;
        }
    }

    /**
     * Parse device type from user agent string.
     */
    public static String parseDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "Tablet";
        } else if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) {
            return "Desktop";
        }
        return "Unknown";
    }

    /**
     * Known fake/placeholder brand names sent by browsers in Sec-CH-UA Client Hints.
     * These are intentionally invalid and should never be used as device names.
     */
    private static final java.util.Set<String> BOGUS_BRANDS = java.util.Set.of(
            "not:a-brand", "not a;brand", "not.a/brand", "not?a_brand",
            "not)a;brand", "(not;browser", "chromium"
    );

    /**
     * Check if a device name is usable (not null, not blank, and not a bogus UA Client Hints brand).
     */
    public static boolean isValidDeviceName(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase().trim();
        // Check if the name starts with a known bogus brand
        for (String bogus : BOGUS_BRANDS) {
            if (lower.startsWith(bogus)) return false;
        }
        return true;
    }

    /**
     * Generate a human-friendly device name from browser, OS, and user info.
     * Example: "Chrome on Windows", "Edge on macOS", "Firefox on Linux".
     * Falls back to "Desktop Device", "Mobile Device" etc.
     */
    public static String generateFriendlyName(String browserType, String osInfo, String deviceType, String userName) {
        String browser = (browserType != null && !"Unknown".equals(browserType)) ? browserType : null;
        String os = parseOsName(osInfo);

        StringBuilder sb = new StringBuilder();
        if (browser != null) {
            sb.append(browser);
            if (os != null) {
                sb.append(" on ").append(os);
            }
        } else if (os != null) {
            sb.append(os).append(" Device");
        } else if (deviceType != null && !"Unknown".equals(deviceType)) {
            sb.append(deviceType).append(" Device");
        } else {
            sb.append("Unknown Device");
        }

        if (userName != null && !userName.isBlank()) {
            sb.append(" (").append(userName).append(")");
        }

        return sb.toString();
    }

    /**
     * Extract a clean OS name from osInfo string.
     */
    private static String parseOsName(String osInfo) {
        if (osInfo == null || osInfo.isBlank()) return null;
        String lower = osInfo.toLowerCase();
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("mac") || lower.contains("darwin")) return "macOS";
        if (lower.contains("linux") && !lower.contains("android")) return "Linux";
        if (lower.contains("android")) return "Android";
        if (lower.contains("ios") || lower.contains("iphone") || lower.contains("ipad")) return "iOS";
        if (lower.contains("chrome os") || lower.contains("chromeos")) return "ChromeOS";
        return osInfo.length() <= 30 ? osInfo : osInfo.substring(0, 30);
    }

    /**
     * Sanitize device name: if the client-sent name is bogus, generate a friendly one.
     */
    public static String sanitizeDeviceName(String clientName, String browserType, String osInfo,
                                             String deviceType, String userName) {
        if (isValidDeviceName(clientName)) {
            return clientName;
        }
        return generateFriendlyName(browserType, osInfo, deviceType, userName);
    }

    /**
     * Parse browser type from user agent string.
     */
    public static String parseBrowserType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Edge";
        } else if (ua.contains("chrome/") && !ua.contains("chromium/")) {
            return "Chrome";
        } else if (ua.contains("firefox/")) {
            return "Firefox";
        } else if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "Safari";
        } else if (ua.contains("opera/") || ua.contains("opr/")) {
            return "Opera";
        }
        return "Unknown";
    }
}
