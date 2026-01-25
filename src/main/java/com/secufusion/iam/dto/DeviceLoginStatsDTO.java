package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for device login statistics and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceLoginStatsDTO {

    // Total device counts
    private long totalDevices;
    private long activeDevices;
    private long inactiveDevices;
    private long blockedDevices;
    private long trustedDevices;
    private long pendingVerificationDevices;

    // Login statistics
    private long totalLogins;
    private long successfulLogins;
    private long failedLogins;
    private long newDeviceLogins;

    // Unique counts
    private long uniqueUsers;
    private long uniqueDevicesPerUser;

    // Breakdown by device type
    private Map<String, Long> loginsByDeviceType;

    // Breakdown by browser
    private Map<String, Long> loginsByBrowser;

    // Breakdown by OS
    private Map<String, Long> loginsByOS;

    // Top devices with login failures (potential security issues)
    private List<DeviceFailureDTO> devicesWithMostFailures;

    // Daily activity trends
    private List<DailyDeviceActivityDTO> dailyActivity;

    // Recent device logins
    private List<LoginDeviceDTO> recentDeviceLogins;

    // Time range
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    /**
     * DTO for devices with login failures.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceFailureDTO {
        private String deviceId;
        private String deviceFingerprint;
        private String deviceName;
        private String deviceType;
        private String osInfo;
        private String browserType;
        private long failureCount;
        private LocalDateTime lastFailureAt;
        private String lastIpAddress;
        private String userId;
        private String username;
    }

    /**
     * DTO for daily device activity trends.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyDeviceActivityDTO {
        private String date;
        private long totalLogins;
        private long successfulLogins;
        private long failedLogins;
        private long uniqueDevices;
        private long newDevices;
    }

    /**
     * DTO for user device summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDeviceSummaryDTO {
        private String userId;
        private String username;
        private long totalDevices;
        private long activeDevices;
        private long trustedDevices;
        private long blockedDevices;
        private LocalDateTime lastLoginAt;
        private String lastDeviceUsed;
    }
}
