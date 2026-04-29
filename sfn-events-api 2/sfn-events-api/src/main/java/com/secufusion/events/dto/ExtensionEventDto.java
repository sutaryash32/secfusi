package com.secufusion.events.dto;

import com.secufusion.events.entity.ExtensionEvent;
import com.secufusion.events.entity.ExtensionEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for extension events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionEventDto {

    private String id;
    private String deviceId;
    private String tenantId;
    private String userId;
    private String userName;

    // Extension info
    private String extensionId;
    private String extensionName;
    private String extensionVersion;

    // Event details
    private ExtensionEventType eventType;
    private LocalDateTime eventTimestamp;
    private String eventDescription;

    // Version change (for updates)
    private String previousVersion;
    private String newVersion;

    // Policy details
    private String policyAction;
    private String policyReason;
    private String policyRuleId;
    private String policyName;

    // Risk details
    private String riskLevel;
    private Integer riskScore;
    private Boolean isWhitelisted;
    private Boolean isBlacklisted;

    // User action
    private String userAction;
    private String userReason;

    // Device context
    private String ipAddress;
    private String browserType;
    private String browserVersion;

    // Additional data
    private Map<String, Object> details;

    /**
     * Convert entity to DTO
     */
    public static ExtensionEventDto fromEntity(ExtensionEvent entity) {
        if (entity == null) {
            return null;
        }
        return ExtensionEventDto.builder()
                .id(entity.getPkExtensionEventId())
                .deviceId(entity.getDevice() != null ? entity.getDevice().getDeviceId() : null)
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .extensionId(entity.getExtensionId())
                .extensionName(entity.getExtensionName())
                .extensionVersion(entity.getExtensionVersion())
                .eventType(entity.getEventType())
                .eventTimestamp(entity.getEventTimestamp())
                .eventDescription(entity.getEventDescription())
                .previousVersion(entity.getPreviousVersion())
                .newVersion(entity.getNewVersion())
                .policyAction(entity.getPolicyAction())
                .policyReason(entity.getPolicyReason())
                .policyRuleId(entity.getPolicyRuleId())
                .policyName(entity.getPolicyName())
                .riskLevel(entity.getRiskLevel())
                .riskScore(entity.getRiskScore())
                .isWhitelisted(entity.getIsWhitelisted())
                .isBlacklisted(entity.getIsBlacklisted())
                .userAction(entity.getUserAction())
                .userReason(entity.getUserReason())
                .ipAddress(entity.getIpAddress())
                .browserType(entity.getBrowserType())
                .browserVersion(entity.getBrowserVersion())
                .details(entity.getDetails())
                .build();
    }
}
