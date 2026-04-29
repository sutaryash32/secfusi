package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs for the Extension Management Dashboard APIs.
 */
public class ExtensionDashboardDTO {

    // ==================== 1. DASHBOARD OVERVIEW ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Overview {
        private long totalUniqueExtensions;
        private long totalInstallations;
        private long activeInstallations;

        private Map<String, Long> riskDistribution;
        private Map<String, Long> policyDistribution;
        private Map<String, Long> statusDistribution;

        private Last30DaysSummary last30Days;
        private List<InventoryItem> topHighRiskExtensions;
        private List<RecentEvent> recentEvents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Last30DaysSummary {
        private long newInstallations;
        private long uninstallations;
        private long blockedAttempts;
        private long warningsShown;
        private long warningsAcknowledged;
    }

    // ==================== 2. EXTENSION INVENTORY ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryItem {
        private String extensionId;
        private String extensionName;
        private String latestVersion;
        private List<String> versions;
        private String riskLevel;
        private int riskScore;
        private String policyAction;
        private long installCount;
        private long activeInstallCount;
        private long deviceCount;
        private long userCount;
        private List<String> highRiskPermissions;
        private LocalDateTime firstSeenAt;
        private LocalDateTime lastSeenAt;
        private boolean isWhitelisted;
        private boolean isBlacklisted;
    }

    // ==================== 3. EXTENSION DETAIL ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtensionDetail {
        private String extensionId;
        private String extensionName;
        private String description;
        private String homepageUrl;
        private String storeUrl;
        private String iconUrl;
        private String latestVersion;
        private String riskLevel;
        private int riskScore;
        private String policyAction;
        private boolean isWhitelisted;
        private boolean isBlacklisted;

        private List<String> permissions;
        private List<String> hostPermissions;
        private List<String> highRiskPermissions;

        private long totalInstallations;
        private long activeInstallations;
        private long deviceCount;
        private long userCount;

        private List<InstallationInfo> installations;
        private List<VersionInfo> versionHistory;
        private List<RecentEvent> eventHistory;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstallationInfo {
        private String deviceId;
        private String deviceName;
        private String userName;
        private String version;
        private String status;
        private LocalDateTime installedAt;
        private LocalDateTime lastSeenAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VersionInfo {
        private String version;
        private LocalDateTime firstSeen;
        private long deviceCount;
    }

    // ==================== 4. TRENDS ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Trends {
        private List<DailyActivity> dailyActivity;
        private List<RiskTrendPoint> riskTrend;
        private List<InventoryItem> topNewExtensions;
        private List<RemovedExtension> topRemovedExtensions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyActivity {
        private LocalDate date;
        private long installed;
        private long uninstalled;
        private long blocked;
        private long warned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskTrendPoint {
        private LocalDate date;
        private long high;
        private long medium;
        private long low;
        private long none;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RemovedExtension {
        private String extensionId;
        private String extensionName;
        private long uninstallCount;
    }

    // ==================== 5. USER RISK PROFILES ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserRiskProfile {
        private String deviceUserId;
        private String userName;
        private String email;
        private String displayName;
        private long totalExtensions;
        private long highRiskExtensions;
        private long blockedExtensions;
        private int riskScore;
        private String riskLevel;
        private LocalDateTime lastActivityAt;
    }

    // ==================== 6. POLICY EFFECTIVENESS ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyEffectiveness {
        private long totalPolicyEvaluations;
        private long blockedCount;
        private long warnedCount;
        private long allowedCount;
        private double warningAcknowledgeRate;
        private long whitelistedCount;
        private long blacklistedCount;

        private List<TopBlockedExtension> topBlockedExtensions;
        private List<TopWarnedExtension> topWarnedExtensions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopBlockedExtension {
        private String extensionId;
        private String extensionName;
        private long blockCount;
        private long uniqueUsers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopWarnedExtension {
        private String extensionId;
        private String extensionName;
        private long warnCount;
        private long acknowledgedCount;
    }

    // ==================== 7. BULK ACTION ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkActionRequest {
        private List<String> extensionIds;
        private String action; // WHITELIST, BLACKLIST, BLOCK, ALLOW, WARN
        private String reason;
        private boolean applyToExisting;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkActionResult {
        private int totalRequested;
        private int updatedCount;
        private String action;
        private String message;
    }

    // ==================== SHARED ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentEvent {
        private String eventId;
        private String extensionId;
        private String extensionName;
        private String eventType;
        private String eventDescription;
        private String deviceName;
        private String userName;
        private String policyAction;
        private String riskLevel;
        private LocalDateTime timestamp;
    }
}
