package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tenant Dashboard Statistics DTO.
 * Contains key metrics for a tenant's dashboard overview.
 *
 * DATA WINDOWS:
 *   activeDevices / devicesByType
 *     → Today only (unique source IPs / user-agents seen since midnight)
 *
 *   totalEventsToday / securityEventsToday
 *     → Since midnight (today, 00:00)
 *
 *   totalEventsThisWeek / securityEventsThisWeek
 *     → Last 7 days (rolling)
 *
 *   totalEventsThisMonth / securityEventsThisMonth
 *     → Last 30 days (rolling)
 *
 *   securityEventsByType
 *     → Last 7 days
 *
 *   organization (totalUsers, activeUsers, etc.)
 *     → ALL-TIME (current state of the user / group / role tables)
 *
 *   recentUsers  → last 10 login events (no time limit — most recent first)
 *   recentDevices → last 10 devices seen today (since midnight)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDashboardStatsDTO {

    /** Unique devices seen today (since midnight). */
    private long activeDevices;
    /** Device type breakdown for today — keys: Desktop, Mobile, Tablet, Unknown. */
    private Map<String, Long> devicesByType;

    /** Total login/audit events since midnight. */
    private long totalEventsToday;
    /** Total login/audit events in the last 7 days. */
    private long totalEventsThisWeek;
    /** Total login/audit events in the last 30 days. */
    private long totalEventsThisMonth;

    /** Security events (LOGIN_FAILURE, ACCOUNT_LOCKED, MFA_FAILURE, …) since midnight. */
    private long securityEventsToday;
    /** Security events in the last 7 days. */
    private long securityEventsThisWeek;
    /** Security events in the last 30 days. */
    private long securityEventsThisMonth;
    /** Security event type breakdown — last 7 days. */
    private Map<String, Long> securityEventsByType;

    // Organization Stats
    private OrganizationStatsDTO organization;

    // Recent Users (last 10 users who logged in)
    private List<RecentUserDTO> recentUsers;

    // Recent Devices (last 10 devices used)
    private List<RecentDeviceDTO> recentDevices;

    // Browser Extension Stats (from sfn-events-api shared database)
    private BrowserEventStatsDTO browserEventStats;

    // Timestamp
    private LocalDateTime generatedAt;

    /**
     * Organization statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationStatsDTO {
        private long totalUsers;
        private long activeUsers;
        private long inactiveUsers;
        private long lockedUsers;
        private long totalGroups;
        private long totalRoles;
        private long adminUsers;
    }

    /**
     * Recent user login information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentUserDTO {
        private String userId;
        private String username;
        private String email;
        private LocalDateTime lastLoginTime;
        private String ipAddress;
        private String location;
        private String deviceType;
        private boolean loginSuccess;
    }

    /**
     * Recent device information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentDeviceDTO {
        private String deviceInfo;
        private String deviceType;
        private String userAgent;
        private String ipAddress;
        private String location;
        private LocalDateTime lastSeenAt;
        private String lastUsedBy;  // username
        private long sessionCount;  // number of sessions from this device today
    }

    public static TenantDashboardStatsDTO empty() {
        return TenantDashboardStatsDTO.builder()
                .activeDevices(0)
                .totalEventsToday(0)
                .totalEventsThisWeek(0)
                .totalEventsThisMonth(0)
                .securityEventsToday(0)
                .securityEventsThisWeek(0)
                .securityEventsThisMonth(0)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
