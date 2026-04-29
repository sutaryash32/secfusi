package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for security event statistics and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEventStatsDTO {

    // Total counts
    private long totalSecurityEvents;
    private long criticalCount;
    private long highCount;
    private long mediumCount;
    private long lowCount;

    // Breakdown by category
    private Map<String, Long> bySeverity;
    private Map<String, Long> byThreatType;
    private Map<String, Long> byRiskLevel;
    private Map<String, Long> byActionTaken;
    private Map<String, Long> byPolicyType;

    // Top items
    private List<TopUserDTO> topUsersWithSecurityEvents;
    private List<TopThreatDTO> topThreatTypes;

    // Trends
    private List<DailySecurityTrendDTO> dailyTrends;

    // Recent critical events
    private List<SecurityEventDTO> recentCriticalEvents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUserDTO {
        private String userName;
        private long eventCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopThreatDTO {
        private String threatType;
        private long eventCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySecurityTrendDTO {
        private String date;
        private long totalEvents;
        private long criticalCount;
        private long highCount;
        private long mediumCount;
        private long lowCount;
    }
}
