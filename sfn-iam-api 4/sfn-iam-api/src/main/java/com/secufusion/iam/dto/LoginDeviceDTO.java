package com.secufusion.iam.dto;

import com.secufusion.iam.entity.UserDeviceLogin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for user device login information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDeviceDTO {

    private Long id;
    private String tenantId;
    private String userId;
    private String username;
    private String deviceId;
    private String deviceFingerprint;
    private String deviceName;
    private String deviceType;
    private String browserType;
    private String osInfo;
    private String userAgent;

    private LocalDateTime firstLoginAt;
    private LocalDateTime lastLoginAt;
    private String lastIpAddress;
    private String lastLocation;

    private Integer loginCount;
    private Integer failedLoginCount;

    private String status;
    private Boolean isTrusted;
    private LocalDateTime trustedAt;
    private String trustedBy;

    private LocalDateTime blockedAt;
    private String blockedBy;
    private String blockReason;

    private String lastSessionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert from UserDeviceLogin entity to DTO.
     */
    public static LoginDeviceDTO fromEntity(UserDeviceLogin entity) {
        if (entity == null) {
            return null;
        }
        return LoginDeviceDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .deviceId(entity.getDeviceId())
                .deviceFingerprint(entity.getDeviceFingerprint())
                .deviceName(entity.getDeviceName())
                .deviceType(entity.getDeviceType())
                .browserType(entity.getBrowserType())
                .osInfo(entity.getOsInfo())
                .userAgent(entity.getUserAgent())
                .firstLoginAt(entity.getFirstLoginAt())
                .lastLoginAt(entity.getLastLoginAt())
                .lastIpAddress(entity.getLastIpAddress())
                .lastLocation(entity.getLastLocation())
                .loginCount(entity.getLoginCount())
                .failedLoginCount(entity.getFailedLoginCount())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .isTrusted(entity.getIsTrusted())
                .trustedAt(entity.getTrustedAt())
                .trustedBy(entity.getTrustedBy())
                .blockedAt(entity.getBlockedAt())
                .blockedBy(entity.getBlockedBy())
                .blockReason(entity.getBlockReason())
                .lastSessionId(entity.getLastSessionId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
