package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for security event responses with all security-related fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEventDTO {

    private String eventId;
    private String url;
    private String timeStamp;
    private String userName;
    private String deviceId;
    private String eventType;

    // Security threat fields
    private String severity;        // critical, high, medium, low
    private String threatType;      // csp_violation, malware, phishing, xss, etc.
    private String threatLevel;     // threat level classification
    private String actionTaken;     // blocked, allowed, warned
    private String riskLevel;       // Critical, High, Medium, Low

    // Policy matching fields
    private String policyName;      // Name of the policy that was triggered
    private String policyType;      // BROWSER_POLICY, NETWORK_POLICY, DLP
    private String filterType;      // WHITELIST, BLACKLIST
    private String patternType;     // DOMAIN, REGEX, URL
    private String matchedPattern;  // The pattern that matched

    // Additional context
    private String domain;
    private String title;
    private String ipAddress;
    private String location;
    private String browserType;
    private String deviceType;

    // Compliance fields
    private String complianceImpact;    // High, Medium, Low
    private String processingStatus;    // Pending, Processed, Reviewed

    // MITRE ATT&CK mapping
    private java.util.List<String> mitreMapping; // e.g., ["T1566.002", "T1185"]

    // Device info
    private String deviceName;          // Device name (e.g., "John's Laptop")
    private String osInfo;              // OS information (e.g., "Windows 11")
    private String deviceStatus;        // Device status: ACTIVE, INACTIVE

    // Device User info (auto-populated by database trigger)
    private String deviceUserId;        // Device user ID (pk_device_user_id)
    private String deviceUserEmail;     // Device user email
    private String deviceUserStatus;    // Device user status: ACTIVE, BLOCKED
    private Boolean linkedToPortalUser; // Whether device user is linked to a portal user

    // Incident info (populated when event is linked to an incident)
    private String incidentId;          // Linked incident ID
    private String incidentNumber;      // Human-readable: INC-000001
    private String incidentStatus;      // OPEN, INVESTIGATING, RESOLVED, CLOSED, FALSE_POSITIVE
    private String incidentPriority;    // P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW
    private Boolean hasIncident;        // Whether event is linked to any incident
}
