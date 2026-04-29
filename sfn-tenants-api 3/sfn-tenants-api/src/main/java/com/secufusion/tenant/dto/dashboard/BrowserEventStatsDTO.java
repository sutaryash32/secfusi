package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Browser Event Statistics DTO.
 * Contains metrics from the browser extension events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserEventStatsDTO {

    // ==================== EVENT COUNTS ====================

    // Total browser events
    private long totalEventsToday;
    private long totalEventsThisWeek;
    private long totalEventsThisMonth;

    // Policy violations
    private long policyViolationsToday;
    private long policyViolationsThisWeek;
    private long policyViolationsThisMonth;

    // Blocked events
    private long blockedEventsToday;
    private long blockedEventsThisWeek;
    private long blockedEventsThisMonth;

    // Security events
    private long browserSecurityEventsToday;
    private long browserSecurityEventsThisWeek;
    private long browserSecurityEventsThisMonth;

    // Active users (users who generated events)
    private long activeUsersToday;
    private long activeUsersThisWeek;
    private long activeUsersThisMonth;

    // ==================== BREAKDOWNS ====================

    // Events by type (WEBSITE_VISIT, FILE_OPERATION, SECURITY_EVENT, etc.)
    private Map<String, Long> eventsByType;

    // Events by category
    private Map<String, Long> eventsByCategory;

    // File operations breakdown
    private Map<String, Long> fileOperationsByType;

    // Security events by threat type
    private Map<String, Long> securityEventsByThreatType;

    // Events by severity
    private Map<String, Long> eventsBySeverity;

    // Top domains visited
    private List<DomainStats> topDomains;

    // Top users by event count
    private List<UserActivityStats> topActiveUsers;

    // Daily trend data
    private List<DailyEventTrend> dailyTrends;

    // ==================== DEVICE STATS ====================

    // Registered devices
    private long totalRegisteredDevices;
    private long activeDevices;
    private long inactiveDevices;
    private long blockedDevices;

    // Devices by type
    private Map<String, Long> devicesByType;

    // Devices by browser
    private Map<String, Long> devicesByBrowser;

    // Devices by OS
    private Map<String, Long> devicesByOs;

    // Extension versions
    private Map<String, Long> devicesByExtensionVersion;

    // Recently active devices
    private List<RecentDeviceInfo> recentDevices;

    // ==================== NESTED CLASSES ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainStats {
        private String domain;
        private long eventCount;
        private long policyViolations;
        private long blockedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityStats {
        private String userName;
        private long eventCount;
        private long policyViolations;
        private LocalDateTime lastActivityTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyEventTrend {
        private String date;
        private long totalEvents;
        private long policyViolations;
        private long blockedEvents;
        private long securityEvents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentDeviceInfo {
        private String deviceId;
        private String deviceName;
        private String userName;
        private String deviceType;
        private String browserType;
        private String osInfo;
        private String extensionVersion;
        private String status;
        private LocalDateTime lastSeenAt;
        private String ipAddress;
        private String location;
    }

    /**
     * Create an empty stats object.
     */
    public static BrowserEventStatsDTO empty() {
        return BrowserEventStatsDTO.builder()
                .totalEventsToday(0)
                .totalEventsThisWeek(0)
                .totalEventsThisMonth(0)
                .policyViolationsToday(0)
                .policyViolationsThisWeek(0)
                .policyViolationsThisMonth(0)
                .blockedEventsToday(0)
                .blockedEventsThisWeek(0)
                .blockedEventsThisMonth(0)
                .browserSecurityEventsToday(0)
                .browserSecurityEventsThisWeek(0)
                .browserSecurityEventsThisMonth(0)
                .activeUsersToday(0)
                .activeUsersThisWeek(0)
                .activeUsersThisMonth(0)
                .totalRegisteredDevices(0)
                .activeDevices(0)
                .inactiveDevices(0)
                .blockedDevices(0)
                .eventsByType(Collections.emptyMap())
                .eventsByCategory(Collections.emptyMap())
                .fileOperationsByType(Collections.emptyMap())
                .securityEventsByThreatType(Collections.emptyMap())
                .eventsBySeverity(Collections.emptyMap())
                .devicesByType(Collections.emptyMap())
                .devicesByBrowser(Collections.emptyMap())
                .devicesByOs(Collections.emptyMap())
                .devicesByExtensionVersion(Collections.emptyMap())
                .topDomains(Collections.emptyList())
                .topActiveUsers(Collections.emptyList())
                .dailyTrends(Collections.emptyList())
                .recentDevices(Collections.emptyList())
                .build();
    }
}
