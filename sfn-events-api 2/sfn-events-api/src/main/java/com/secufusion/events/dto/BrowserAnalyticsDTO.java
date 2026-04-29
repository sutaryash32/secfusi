    package com.secufusion.events.dto;

    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.time.LocalDateTime;
    import java.util.List;
    import java.util.Map;

    /**
     * Browser Usage Analytics DTO containing file operations, DLP stats,
     * activity trends, and domain analytics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class BrowserAnalyticsDTO {

        // Tenant Info
        private String tenantId;
        private String tenantName;

        // Analytics Period
        private String period; // "7_DAYS", "30_DAYS", "90_DAYS"
        private LocalDateTime startDate;
        private LocalDateTime endDate;

        // File Operations Stats
        private long totalDownloads;
        private long totalUploads;
        private long blockedDownloads;
        private long blockedUploads;

        // Daily Activity Trends (for chart)
        private List<DailyTrendDTO> dailyActivityTrends;

        // Top Accessed Domains
        private List<DomainAccessDTO> topAccessedDomains;
        private long totalDomainVisits;

        // Communication Platforms (placeholder for future)
        private List<PlatformUsageDTO> communicationPlatforms;

        // Generated timestamp
        private LocalDateTime generatedAt;

        /**
         * Daily activity trend data point.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DailyTrendDTO {
            private String date; // "2026-01-25"
            private long events;
            private long activeDevices;
            private long downloads;
            private long uploads;
            private long violations;
        }

        /**
         * Domain access statistics with ranking.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DomainAccessDTO {
            private int rank;
            private String domain;
            private long visits;
            private double percentage;
            private String category;
        }

        /**
         * Communication platform usage stats.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PlatformUsageDTO {
            private String platformName; // "Slack", "Teams", "Zoom", etc.
            private long activeUsers;
            private long totalSessions;
            private long totalDuration; // in seconds
        }

        /**
         * Create an empty analytics response for a tenant.
         */
        public static BrowserAnalyticsDTO empty(String tenantId, String period) {
            return BrowserAnalyticsDTO.builder()
                    .tenantId(tenantId)
                    .period(period)
                    .totalDownloads(0)
                    .totalUploads(0)
                    .blockedDownloads(0)
                    .blockedUploads(0)
                    .totalDomainVisits(0)
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
    }
