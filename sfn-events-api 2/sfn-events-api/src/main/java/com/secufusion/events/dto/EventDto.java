package com.secufusion.events.dto;

import lombok.Data;

import java.util.List;

@Data
public class EventDto {

    // Event identification
    String id;                  // Primary key (pk_event_id)
    String tenantId;            // Tenant identifier

    // Existing fields
    String url;
    String domain;              // Domain extracted from URL
    String timeStamp;
    String browserType;
    String deviceType;
    String ipAddress;
    String location;            // Location info

    // New fields for event classification and enhanced tracking
    String eventType;           // Maps to EventType enum (WEBSITE_VISIT, TRACKING_ACTIVITY, etc.)
    String deviceId;            // FK to Device entity
    String title;               // Page title for WEBSITE_VISIT
    Long durationSeconds;       // Time spent on page
    String details;             // JSON string for type-specific data
    String category;            // URL category
    Boolean isPolicyViolation;  // True if event triggered a policy violation
    String policyRuleId;        // Policy rule ID that was violated

    // File operation specific fields
    String fileOperationType;   // Maps to FileOperationType enum (DOWNLOAD, UPLOAD, PRINT, CLIPBOARD_COPY, CLIPBOARD_PASTE)
    String fileName;            // Name of the file
    Long fileSize;              // Size of the file in bytes
    String fileType;            // MIME type or extension of the file
    Boolean isBlocked;          // True if the file operation was blocked

    // Security threat specific fields
    Boolean isSecurityEvent;    // True if this is a security-related event
    String severity;            // Severity level: critical, high, medium, low
    String threatType;          // Type of threat: csp_violation, malware, phishing, xss, etc.
    String threatLevel;         // Threat level classification
    String actionTaken;         // Action taken: blocked, allowed, warned
    String riskLevel;           // Risk assessment: Critical, High, Medium, Low

    // Policy matching fields
    String policyName;          // Name of the policy that was triggered
    String policyType;          // Type: BROWSER_POLICY, NETWORK_POLICY, DLP
    String filterType;          // URL filter type: WHITELIST, BLACKLIST
    String patternType;         // Pattern type: DOMAIN, REGEX, URL
    String matchedPattern;      // The pattern that matched

    // Compliance fields
    String complianceImpact;    // Impact on compliance: High, Medium, Low
    String processingStatus;    // Processing status: Pending, Processed, Reviewed

    // MITRE ATT&CK mapping
    List<String> mitreMapping;  // MITRE ATT&CK technique IDs (e.g., ["T1566.002", "T1185"])

    // Device info (populated when returning events)
    String deviceName;          // Device name (e.g., "John's Laptop")
    String osInfo;              // OS information (e.g., "Windows 11")
    String deviceStatus;        // Device status: ACTIVE, INACTIVE
    String userName;            // User associated with the event

    // Device User info (auto-populated by database trigger)
    String deviceUserId;        // Device user ID (pk_device_user_id)
    String deviceUserEmail;     // Device user email
    String deviceUserStatus;    // Device user status: ACTIVE, BLOCKED
    Boolean linkedToPortalUser; // Whether device user is linked to a portal user
}
