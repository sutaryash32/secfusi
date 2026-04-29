package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Read-only entity mapping to the events table in the shared database.
 * Used for dashboard statistics and reporting.
 */
@Entity
@Table(name = "events")
@Immutable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserEvent {

    @Id
    @Column(name = "pk_event_id")
    private String pkEventId;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Column(name = "browser_type", columnDefinition = "text")
    private String browserType;

    @Column(name = "device_type", columnDefinition = "text")
    private String deviceType;

    @Column(name = "time_stamp", columnDefinition = "timestamptz")
    private LocalDateTime timeStamp;

    @Column(name = "fk_tenant_id")
    private String tenantId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "fk_device_id")
    private String deviceId;

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

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "is_policy_violation")
    private Boolean isPolicyViolation;

    @Column(name = "policy_rule_id", length = 50)
    private String policyRuleId;

    // File operation fields
    @Column(name = "file_operation_type", length = 20)
    private String fileOperationType;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "is_blocked")
    private Boolean isBlocked;

    // Security threat fields
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
}
