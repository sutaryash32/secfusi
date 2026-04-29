package com.secufusion.events.dto;

import com.secufusion.events.entity.DeviceUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for DeviceUser entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceUserDTO {

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

    /**
     * Whether this device user is linked to a portal user.
     */
    private boolean linkedToPortal;

    // Enriched fields (populated by service)
    private long deviceCount;
    private long eventCount;
    private long securityEventCount;

    /**
     * Convert from entity to DTO.
     */
    public static DeviceUserDTO fromEntity(DeviceUser entity) {
        if (entity == null) {
            return null;
        }
        return DeviceUserDTO.builder()
                .deviceUserId(entity.getPkDeviceUserId())
                .tenantId(entity.getTenantId())
                .email(entity.getEmail())
                .userName(entity.getUserName())
                .displayName(entity.getDisplayName())
                .portalUserId(entity.getPortalUserId())
                .status(entity.getStatus())
                .firstSeenAt(entity.getFirstSeenAt())
                .lastSeenAt(entity.getLastSeenAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .linkedToPortal(entity.getPortalUserId() != null)
                .build();
    }
}
