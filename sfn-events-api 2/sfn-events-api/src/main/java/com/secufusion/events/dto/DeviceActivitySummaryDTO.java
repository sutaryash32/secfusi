package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aggregated activity summary for a device.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceActivitySummaryDTO {

    // Device Info
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String userName;

    // Event Counts
    private long totalEvents;
    private long eventsToday;
    private long eventsThisWeek;
    private long eventsThisMonth;

    // Event Breakdown by Type (WEBSITE_VISIT, FILE_OPERATION, etc.)
    private Map<String, Long> eventsByType;

    // Website Visit Stats
    private long uniqueDomainsVisited;
    private long totalTimeSpentSeconds;
    private Map<String, Long> topDomains;

    // Policy Violations
    private long policyViolationsToday;
    private long policyViolationsThisWeek;
    private Map<String, Long> violationsByType;

    // Activity Timeline
    private LocalDateTime firstActivityAt;
    private LocalDateTime lastActivityAt;

    // Timestamp
    private LocalDateTime generatedAt;

    /**
     * Create an empty summary for a device.
     */
    public static DeviceActivitySummaryDTO empty(String deviceId) {
        return DeviceActivitySummaryDTO.builder()
                .deviceId(deviceId)
                .totalEvents(0)
                .eventsToday(0)
                .eventsThisWeek(0)
                .eventsThisMonth(0)
                .uniqueDomainsVisited(0)
                .totalTimeSpentSeconds(0)
                .policyViolationsToday(0)
                .policyViolationsThisWeek(0)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
