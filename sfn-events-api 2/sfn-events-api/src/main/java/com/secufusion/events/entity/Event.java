package com.secufusion.events.entity;

import com.secufusion.events.dto.EventDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_events_device", columnList = "fk_device_id"),
        @Index(name = "idx_events_event_type", columnList = "event_type"),
        @Index(name = "idx_events_timestamp", columnList = "time_stamp"),
        @Index(name = "idx_events_domain", columnList = "domain"),
        @Index(name = "idx_events_user", columnList = "user_name"),
        @Index(name = "idx_events_violation", columnList = "is_policy_violation"),
        @Index(name = "idx_events_file_op_type", columnList = "file_operation_type"),
        @Index(name = "idx_events_is_blocked", columnList = "is_blocked"),
        @Index(name = "idx_events_security", columnList = "is_security_event"),
        @Index(name = "idx_events_severity", columnList = "severity"),
        @Index(name = "idx_events_threat_type", columnList = "threat_type"),
        @Index(name = "idx_events_risk_level", columnList = "risk_level"),
        @Index(name = "idx_events_processing_status", columnList = "processing_status"),
        @Index(name = "idx_events_device_user", columnList = "fk_device_user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_event_id", nullable = false, updatable = false)
    private String pkEventId;

    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "browser_type", columnDefinition = "text")
    private String browserType;

    @Column(name = "device_type", columnDefinition = "text")
    private String deviceType;

    @Column(name = "time_stamp", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime timeStamp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    private String userName;

    // New fields for event classification and device tracking

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private EventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_device_id")
    private Device device;

    /**
     * Foreign key to device_user table.
     * Auto-populated by database trigger on INSERT.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_device_user_id")
    private DeviceUser deviceUser;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "is_policy_violation")
    private Boolean isPolicyViolation;

    @Column(name = "policy_rule_id", length = 50)
    private String policyRuleId;

    // File operation specific fields
    @Enumerated(EnumType.STRING)
    @Column(name = "file_operation_type", length = 30)
    private FileOperationType fileOperationType;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "is_blocked")
    private Boolean isBlocked;

    // Security threat specific fields
    @Column(name = "is_security_event")
    private Boolean isSecurityEvent;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "threat_type", length = 50)
    private String threatType;

    @Column(name = "threat_level", length = 20)
    private String threatLevel;

    @Column(name = "action_taken", length = 50)
    private String actionTaken;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    // Policy matching fields
    @Column(name = "policy_name", length = 200)
    private String policyName;

    @Column(name = "policy_type", length = 50)
    private String policyType;

    @Column(name = "filter_type", length = 20)
    private String filterType;

    @Column(name = "pattern_type", length = 20)
    private String patternType;

    @Column(name = "matched_pattern", length = 500)
    private String matchedPattern;

    // Compliance fields
    @Column(name = "compliance_impact", length = 20)
    private String complianceImpact;

    @Column(name = "processing_status", length = 20)
    private String processingStatus;

    // MITRE ATT&CK mapping
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mitre_mapping", columnDefinition = "jsonb")
    private List<String> mitreMapping;

    /**
     * Factory method to create Event from EventDto (legacy - without device).
     */
    public static Event from(EventDto eventDto, Tenant tenant, String userName) {
        return Event.builder()
                .url(eventDto.getUrl())
                .timeStamp(LocalDateTime.parse(eventDto.getTimeStamp()))
                .tenant(tenant)
                .userName(userName)
                .browserType(eventDto.getBrowserType())
                .deviceType(eventDto.getDeviceType())
                .ipAddress(eventDto.getIpAddress())
                .eventType(resolveEventType(eventDto.getEventType(), eventDto.getFileOperationType()))
                .title(eventDto.getTitle())
                .domain(extractDomain(eventDto.getUrl()))
                .durationSeconds(eventDto.getDurationSeconds())
                .details(sanitizeJsonDetails(eventDto.getDetails()))
                .category(eventDto.getCategory())
                .isPolicyViolation(eventDto.getIsPolicyViolation())
                .policyRuleId(eventDto.getPolicyRuleId())
                // File operation fields
                .fileOperationType(parseFileOperationType(eventDto.getFileOperationType()))
                .fileName(eventDto.getFileName())
                .fileSize(eventDto.getFileSize())
                .fileType(eventDto.getFileType())
                .isBlocked(eventDto.getIsBlocked())
                // Security threat fields
                .isSecurityEvent(eventDto.getIsSecurityEvent())
                .severity(eventDto.getSeverity())
                .threatType(eventDto.getThreatType())
                .threatLevel(eventDto.getThreatLevel())
                .actionTaken(eventDto.getActionTaken())
                .riskLevel(eventDto.getRiskLevel())
                // Policy matching fields
                .policyName(eventDto.getPolicyName())
                .policyType(eventDto.getPolicyType())
                .filterType(eventDto.getFilterType())
                .patternType(eventDto.getPatternType())
                .matchedPattern(eventDto.getMatchedPattern())
                // Compliance fields
                .complianceImpact(eventDto.getComplianceImpact())
                .processingStatus(eventDto.getProcessingStatus() != null ? eventDto.getProcessingStatus() : "Pending")
                // MITRE ATT&CK mapping
                .mitreMapping(eventDto.getMitreMapping())
                .build();
    }

    /**
     * Factory method to create Event from EventDto with Device.
     */
    public static Event from(EventDto eventDto, Tenant tenant, String userName, Device device) {
        Event event = from(eventDto, tenant, userName);
        event.setDevice(device);
        return event;
    }

    /**
     * Convert Event to DTO.
     */
    public static EventDto toDto(Event event) {
        EventDto dto = new EventDto();
        // Event identification
        dto.setId(event.getPkEventId());
        dto.setTenantId(event.getTenant() != null ? event.getTenant().getTenantID() : null);
        // Core fields
        dto.setUrl(event.getUrl());
        dto.setDomain(event.getDomain());
        dto.setTimeStamp(String.valueOf(event.getTimeStamp()));
        dto.setBrowserType(event.getBrowserType());
        dto.setDeviceType(event.getDeviceType());
        dto.setIpAddress(event.getIpAddress());
        dto.setLocation(event.getLocation());
        dto.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
        dto.setDeviceId(event.getDevice() != null ? event.getDevice().getDeviceId() : null);
        dto.setTitle(event.getTitle());
        dto.setDurationSeconds(event.getDurationSeconds());
        dto.setDetails(event.getDetails());
        dto.setCategory(event.getCategory());
        dto.setIsPolicyViolation(event.getIsPolicyViolation());
        dto.setPolicyRuleId(event.getPolicyRuleId());
        // File operation fields
        dto.setFileOperationType(event.getFileOperationType() != null ? event.getFileOperationType().name() : null);
        dto.setFileName(event.getFileName());
        dto.setFileSize(event.getFileSize());
        dto.setFileType(event.getFileType());
        dto.setIsBlocked(event.getIsBlocked());
        // Security threat fields
        dto.setIsSecurityEvent(event.getIsSecurityEvent());
        dto.setSeverity(event.getSeverity());
        dto.setThreatType(event.getThreatType());
        dto.setThreatLevel(event.getThreatLevel());
        dto.setActionTaken(event.getActionTaken());
        dto.setRiskLevel(event.getRiskLevel());
        // Policy matching fields
        dto.setPolicyName(event.getPolicyName());
        dto.setPolicyType(event.getPolicyType());
        dto.setFilterType(event.getFilterType());
        dto.setPatternType(event.getPatternType());
        dto.setMatchedPattern(event.getMatchedPattern());
        // Compliance fields
        dto.setComplianceImpact(event.getComplianceImpact());
        dto.setProcessingStatus(event.getProcessingStatus());
        // MITRE ATT&CK mapping
        dto.setMitreMapping(event.getMitreMapping());
        // Device info
        if (event.getDevice() != null) {
            dto.setDeviceName(event.getDevice().getDeviceName());
            dto.setOsInfo(event.getDevice().getOsInfo());
            dto.setDeviceStatus(event.getDevice().getStatus() != null ? event.getDevice().getStatus().name() : null);
        }
        dto.setUserName(event.getUserName());
        // Device User info (auto-populated by database trigger)
        if (event.getDeviceUser() != null) {
            dto.setDeviceUserId(event.getDeviceUser().getPkDeviceUserId());
            dto.setDeviceUserEmail(event.getDeviceUser().getEmail());
            dto.setDeviceUserStatus(event.getDeviceUser().getStatus());
            dto.setLinkedToPortalUser(event.getDeviceUser().getPortalUserId() != null);
        }
        return dto;
    }

    /**
     * Ensure details value is valid JSON for the jsonb column.
     * If a plain string is sent (not starting with { or [), wrap it as {"message":"..."}.
     */
    private static String sanitizeJsonDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String trimmed = details.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        // Plain string — wrap as JSON object
        return "{\"message\":" + escapeJsonString(trimmed) + "}";
    }

    /**
     * Escape a string value for safe embedding in JSON.
     */
    private static String escapeJsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    /**
     * Extract domain from URL.
     */
    private static String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URL parsed = new URL(url);
            return parsed.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse event type from string, defaulting to WEBSITE_VISIT.
     */
    private static EventType parseEventType(String eventTypeStr) {
        if (eventTypeStr == null || eventTypeStr.isBlank()) {
            return EventType.WEBSITE_VISIT;
        }
        try {
            return EventType.valueOf(eventTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EventType.WEBSITE_VISIT;
        }
    }

    /**
     * Resolve specific EventType from eventType + fileOperationType.
     * When eventType=FILE_OPERATION and fileOperationType is set, maps to the specific sub-type
     * (FILE_DOWNLOAD, FILE_UPLOAD, FILE_PRINT, FILE_CLIPBOARD_COPY, FILE_CLIPBOARD_PASTE).
     */
    private static EventType resolveEventType(String eventTypeStr, String fileOperationTypeStr) {
        EventType eventType = parseEventType(eventTypeStr);
        if (eventType == EventType.FILE_OPERATION && fileOperationTypeStr != null) {
            FileOperationType fileOpType = parseFileOperationType(fileOperationTypeStr);
            if (fileOpType != null) {
                return switch (fileOpType) {
                    case DOWNLOAD, FILE_DOWNLOAD -> EventType.FILE_DOWNLOAD;
                    case UPLOAD, FILE_UPLOAD      -> EventType.FILE_UPLOAD;
                    case PRINT                    -> EventType.FILE_PRINT;
                    case CLIPBOARD_COPY           -> EventType.FILE_CLIPBOARD_COPY;
                    case CLIPBOARD_PASTE          -> EventType.FILE_CLIPBOARD_PASTE;
                    case BLOCKED_EXTENSION_INSTALLED, PII_IN_FILE,
                         URL_BLOCKED, EXTENSION_COMPLIANCE_BLOCK,
                         PHISHING_DETECTION, CREDENTIAL_THEFT_BLOCKED,
                         PHISHING_FALSE_POSITIVE, SCRIPT_INJECTION,
                         FORM_HIJACKING -> eventType;
                };
            }
        }
        return eventType;
    }

    /**
     * Parse file operation type from string, returning null if not valid.
     */
    private static FileOperationType parseFileOperationType(String fileOpTypeStr) {
        if (fileOpTypeStr == null || fileOpTypeStr.isBlank()) {
            return null;
        }
        try {
            return FileOperationType.valueOf(fileOpTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
