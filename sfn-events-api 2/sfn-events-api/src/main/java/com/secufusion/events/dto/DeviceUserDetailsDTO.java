package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive DTO for Device User details.
 * Contains all information about a device user including devices, events, extensions, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceUserDetailsDTO {

    // ==================== BASIC INFO ====================
    private String deviceUserId;
    private String tenantId;
    private String email;
    private String userName;
    private String displayName;
    private String portalUserId;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean linkedToPortal;

    // ==================== SUMMARY COUNTS ====================
    private long totalDevices;
    private long activeDevices;
    private long inactiveDevices;
    private long blockedDevices;

    private long totalEvents;
    private long eventsLast24Hours;
    private long eventsLast7Days;
    private long eventsLast30Days;

    private long securityEvents;
    private long securityEventsLast30Days;
    private long policyViolations;
    private long policyViolationsLast30Days;
    private long blockedOperations;

    private long totalExtensions;
    private long highRiskExtensions;
    private long blockedExtensions;

    // ==================== DEVICES LIST ====================
    private List<DeviceInfo> devices;

    // ==================== ACTIVITY BREAKDOWN ====================
    private List<EventTypeBreakdown> activityBreakdown;

    // ==================== TOP DOMAINS ====================
    private List<DomainStats> topVisitedDomains;

    // ==================== RECENT ACTIVITY ====================
    private List<RecentActivity> recentActivity;

    // ==================== EXTENSIONS ====================
    private List<ExtensionInfo> extensions;

    // ==================== FILE OPERATIONS ====================
    private FileOperationsSummary fileOperations;

    // ==================== RISK ASSESSMENT ====================
    private RiskAssessment riskAssessment;

    // ==================== BROWSER USAGE ====================
    private Map<String, Long> browserUsage;

    // ==================== LOCATION INFO ====================
    private List<LocationInfo> locations;

    // ==================== POLICY VIOLATIONS ====================
    private List<PolicyViolationInfo> recentPolicyViolations;

    // ==================== SECURITY EVENTS ====================
    private List<SecurityEventInfo> recentSecurityEvents;

    // ==================== INNER CLASSES ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeviceInfo {
        private String deviceId;
        private String deviceName;
        private String deviceType;
        private String browserType;
        private String osInfo;
        private String extensionVersion;
        private String status;
        private String ipAddress;
        private String location;
        private LocalDateTime firstSeenAt;
        private LocalDateTime lastSeenAt;
        private long eventCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EventTypeBreakdown {
        private String eventType;
        private long count;
        private double percentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DomainStats {
        private String domain;
        private long visitCount;
        private double percentage;
        private LocalDateTime lastVisited;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentActivity {
        private String eventId;
        private String eventType;
        private String description;
        private String url;
        private String domain;
        private String deviceName;
        private LocalDateTime timestamp;
        private String severity;
        private boolean isPolicyViolation;
        private boolean isSecurityEvent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtensionInfo {
        private String extensionId;
        private String extensionName;
        private String version;
        private String riskLevel;
        private String policyAction;
        private String status;
        private List<String> deviceNames;
        private LocalDateTime installedAt;
        private List<String> highRiskPermissions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileOperationsSummary {
        private long totalDownloads;
        private long totalUploads;
        private long blockedDownloads;
        private long blockedUploads;
        private long printOperations;
        private long clipboardOperations;
        private List<RecentFileOperation> recentOperations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentFileOperation {
        private String operationType;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private boolean blocked;
        private String deviceName;
        private LocalDateTime timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskAssessment {
        private int riskScore;           // 0-100
        private String riskLevel;        // Low, Medium, High, Critical
        private int highRiskExtensions;
        private int policyViolationsCount;
        private int securityEventsCount;
        private int blockedOperationsCount;
        private List<String> riskFactors;
        private LocalDateTime assessedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationInfo {
        private String ipAddress;
        private String location;
        private String country;
        private String city;
        private long accessCount;
        private LocalDateTime lastSeenAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyViolationInfo {
        private String eventId;
        private String policyName;
        private String policyType;
        private String url;
        private String domain;
        private String actionTaken;
        private String severity;
        private String deviceName;
        private LocalDateTime timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SecurityEventInfo {
        private String eventId;
        private String threatType;
        private String severity;
        private String url;
        private String domain;
        private String actionTaken;
        private String deviceName;
        private LocalDateTime timestamp;
    }
}
