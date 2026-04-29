package com.secufusion.events.dto;

import com.secufusion.events.entity.ExtensionStatus;
import com.secufusion.events.entity.InstalledExtension;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for installed extension data.
 * Used both for receiving extension info from browser and returning evaluated results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstalledExtensionDto {

    // ===== Identification =====
    private String id;
    private String extensionId;
    private String extensionName;
    private String version;
    private String description;
    private String homepageUrl;
    private String storeUrl;
    private String iconUrl;

    // ===== Permissions =====
    private List<String> permissions;
    private List<String> hostPermissions;
    private List<String> optionalPermissions;

    // ===== Status & Policy Evaluation =====
    private ExtensionStatus status;
    private String policyAction;
    private String policyReason;
    private String matchedPolicyId;
    private Boolean isWhitelisted;
    private Boolean isBlacklisted;

    // ===== Risk Assessment =====
    private String riskLevel;
    private Integer riskScore;
    private List<String> highRiskPermissions;

    // ===== Installation Info =====
    private String installType;
    private Boolean isManaged;
    private Boolean mayDisable;
    private Boolean offlineEnabled;

    // ===== Timestamps =====
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime installedAt;

    /**
     * Convert entity to DTO.
     */
    public static InstalledExtensionDto fromEntity(InstalledExtension entity) {
        if (entity == null) {
            return null;
        }
        return InstalledExtensionDto.builder()
                .id(entity.getPkInstalledExtensionId())
                .extensionId(entity.getExtensionId())
                .extensionName(entity.getExtensionName())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .homepageUrl(entity.getHomepageUrl())
                .storeUrl(entity.getStoreUrl())
                .iconUrl(entity.getIconUrl())
                .permissions(entity.getPermissions())
                .hostPermissions(entity.getHostPermissions())
                .optionalPermissions(entity.getOptionalPermissions())
                .status(entity.getStatus())
                .policyAction(entity.getPolicyAction())
                .policyReason(entity.getPolicyReason())
                .matchedPolicyId(entity.getMatchedPolicyId())
                .isWhitelisted(entity.getIsWhitelisted())
                .isBlacklisted(entity.getIsBlacklisted())
                .riskLevel(entity.getRiskLevel())
                .riskScore(entity.getRiskScore())
                .highRiskPermissions(entity.getHighRiskPermissions())
                .installType(entity.getInstallType())
                .isManaged(entity.getIsManaged())
                .mayDisable(entity.getMayDisable())
                .offlineEnabled(entity.getOfflineEnabled())
                .firstSeenAt(entity.getFirstSeenAt())
                .lastSeenAt(entity.getLastSeenAt())
                .installedAt(entity.getInstalledAt())
                .build();
    }
}
