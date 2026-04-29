package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Security Events Dashboard UI.
 * Contains summary stats, event type breakdown, and recent events list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityDashboardDTO {

    // Summary Statistics (Top Cards)
    private SummaryStats summary;

    // Event Types Breakdown (Left Sidebar)
    private List<EventTypeCount> eventsByType;

    // Recent Events (Table)
    private List<RecentEventDTO> recentEvents;

    // Pagination
    private PaginationInfo pagination;

    // Filter Period
    private String period; // "24h", "7d", "30d"

    // Generated timestamp
    private LocalDateTime generatedAt;

    /**
     * Summary statistics for dashboard cards.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private long totalEvents;
        private double totalEventsChange;  // Percentage change vs previous period
        private long criticalEvents;       // Immediate attention
        private long highPriorityEvents;   // Under review
        private long mediumLowEvents;      // For monitoring
    }

    /**
     * Event type count for sidebar.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventTypeCount {
        private String type;      // SECURITY_THREAT, POLICY_VIOLATION, DLP_ALERT, COMPLIANCE
        private String label;     // Display label
        private long count;
        private String color;     // Hex color for UI
    }

    /**
     * Recent event for table display.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentEventDTO {
        private String eventId;
        private LocalDateTime time;
        private String timeDisplay;    // "12:13 PM"
        private String dateDisplay;    // "Oct 07"

        // Event Type
        private String type;           // SECURITY_THREAT, POLICY_VIOLATION, DLP_ALERT
        private String typeLabel;      // "Security Threat", "Policy Violation", "Data Leak Prevention"

        // Device Info
        private String deviceId;
        private String deviceName;     // "Device bdr_175"

        // Severity
        private String severity;       // critical, high, medium, low
        private String severityLabel;  // "Critical", "High", "Medium", "Low"

        // Details
        private String details;        // "Multiple failed", "PII data detected"
        private String url;            // Short URL for display
        private String fullUrl;        // Full URL for tooltip/detail view

        // User Info
        private String userName;

        // Action
        private String actionTaken;    // blocked, warned, allowed

        // Threat Info (optional)
        private String threatType;
        private String policyName;

        // Incident Info
        private String incidentId;
        private String incidentNumber;
        private String incidentStatus;
        private Boolean hasIncident;
    }

    /**
     * Event Details for modal/detail view.
     * Contains ALL possible data from Event, Device, and Auditable entities.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetailDTO {
        // ==================== BASIC INFORMATION ====================
        private String eventId;
        private String eventType;            // WEBSITE_VISIT, FILE_OPERATION, SECURITY_THREAT, etc.
        private String eventTypeLabel;       // Human-readable label
        private String timestamp;            // "7/10/2025, 5:43:45 pm"
        private String url;
        private String domain;
        private String title;
        private String category;
        private String sessionId;
        private String userAgent;
        private Long durationSeconds;        // Time spent on page/activity

        // ==================== EVENT CLASSIFICATION ====================
        private boolean isSecurityEvent;
        private boolean isPolicyViolation;
        private boolean isBlocked;
        private String riskLevel;            // HIGH, MEDIUM, LOW, Critical
        private String complianceImpact;     // High, Medium, Low
        private String processingStatus;     // Pending, Processed, Reviewed
        private java.util.List<String> mitreMapping; // MITRE ATT&CK technique IDs

        // ==================== THREAT DETAILS ====================
        private String threatType;           // phishing, malware, csp_violation, xss, etc.
        private String threatLevel;          // High, Medium, Low
        private String severity;             // critical, high, medium, low
        private String actionTaken;          // blocked, warned, allowed

        // ==================== POLICY DETAILS ====================
        private String policyRuleId;
        private String policyName;
        private String policyType;           // URL_FILTER, CONTENT_FILTER, DLP, etc.
        private String filterType;           // BLACKLIST, WHITELIST, PATTERN
        private String patternType;          // EXACT, WILDCARD, REGEX
        private String matchedPattern;       // The pattern that matched

        // ==================== FILE OPERATION DETAILS ====================
        private String fileOperationType;    // DOWNLOAD, UPLOAD, DELETE, COPY, etc.
        private String fileName;
        private Long fileSize;               // in bytes
        private String fileType;             // MIME type or extension

        // ==================== DEVICE INFORMATION ====================
        private String deviceId;
        private String deviceName;
        private String deviceType;           // Desktop, Mobile, Tablet
        private String browserType;          // Chrome, Firefox, Edge, etc.
        private String osInfo;               // Windows 10, macOS, Linux, etc.
        private String extensionVersion;     // Browser extension version
        private String deviceFingerprint;
        private String deviceStatus;         // ACTIVE, INACTIVE, BLOCKED
        private String deviceFirstSeenAt;
        private String deviceLastSeenAt;

        // ==================== NETWORK INFORMATION ====================
        private String ipAddress;
        private String location;             // Geo-location

        // ==================== USER INFORMATION ====================
        private String userId;
        private String userName;
        private String userEmail;
        private String tenantId;

        // ==================== DEVICE USER INFORMATION ====================
        private String deviceUserId;
        private String deviceUserEmail;
        private String deviceUserStatus;
        private Boolean linkedToPortalUser;

        // ==================== AUDIT INFORMATION ====================
        private String createdAt;            // Event creation timestamp
        private String updatedAt;            // Last update timestamp
        private String createdBy;
        private String updatedBy;

        // ==================== RAW DATA ====================
        private String details;              // JSON string for raw details/metadata

        // ==================== INCIDENT INFORMATION ====================
        private String incidentId;           // Linked incident ID
        private String incidentNumber;       // Human-readable: INC-000001
        private String incidentStatus;       // OPEN, INVESTIGATING, RESOLVED, CLOSED
        private String incidentPriority;     // P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW
        private String incidentTitle;        // Incident title
        private Boolean hasIncident;         // Whether event is linked to any incident
    }

    /**
     * Pagination info.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /**
     * Filter options for the dashboard.
     * Note: Date range filtering is done via start/end parameters on the API endpoint.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterOptions {
        private List<String> eventTypes;      // Available event types
        private List<String> severityLevels;  // critical, high, medium, low
        private List<String> riskLevels;      // Critical, High, Medium, Low
        private List<String> actionTypes;     // blocked, allowed, warned
        private List<String> devices;         // Available device names
        private List<String> users;           // Available usernames
    }

    /**
     * Create empty dashboard response.
     */
    public static SecurityDashboardDTO empty(String period) {
        return SecurityDashboardDTO.builder()
                .summary(SummaryStats.builder()
                        .totalEvents(0)
                        .totalEventsChange(0.0)
                        .criticalEvents(0)
                        .highPriorityEvents(0)
                        .mediumLowEvents(0)
                        .build())
                .eventsByType(List.of())
                .recentEvents(List.of())
                .pagination(PaginationInfo.builder()
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .build())
                .period(period)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
