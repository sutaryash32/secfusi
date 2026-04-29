package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregated activity summary for a tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantActivitySummaryDTO {

    // Tenant Info
    private String tenantId;
    private String tenantName;

    // Device Stats
    private long totalDevices;
    private long activeDevices;
    private long inactiveDevices;
    private long blockedDevices;
    private Map<String, Long> devicesByType;

    // Event Stats
    private long totalEventsToday;
    private long totalEventsThisWeek;
    private long totalEventsThisMonth;
    private Map<String, Long> eventsByType;

    // User Activity
    private long activeUsersToday;
    private long activeUsersThisWeek;
    private List<TopUserDTO> topActiveUsers;

    // Website Stats
    private long uniqueDomainsToday;
    private Map<String, Long> topDomains;
    private Map<String, Long> domainsByCategory;

    // Policy Violations
    private long violationsToday;
    private long violationsThisWeek;
    private Map<String, Long> violationsByType;

    // Recent Activity
    private List<RecentEventDTO> recentEvents;
    private List<DeviceResponse> recentDevices;

    // Timestamp
    private LocalDateTime generatedAt;

    /**
     * Top active user information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUserDTO {
        private String userId;
        private String userName;
        private long eventCount;
        private LocalDateTime lastActivityAt;
    }

    /**
     * Recent event information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentEventDTO {
        private String eventId;
        private String eventType;
        private String url;
        private String title;
        private String userName;
        private String deviceId;
        private LocalDateTime timestamp;
    }

    /**
     * Create an empty summary for a tenant.
     */
    public static TenantActivitySummaryDTO empty(String tenantId) {
        return TenantActivitySummaryDTO.builder()
                .tenantId(tenantId)
                .totalDevices(0)
                .activeDevices(0)
                .inactiveDevices(0)
                .blockedDevices(0)
                .totalEventsToday(0)
                .totalEventsThisWeek(0)
                .totalEventsThisMonth(0)
                .activeUsersToday(0)
                .activeUsersThisWeek(0)
                .uniqueDomainsToday(0)
                .violationsToday(0)
                .violationsThisWeek(0)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
