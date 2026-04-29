package com.secufusion.events.dto;

import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.DeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO containing device information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {

    private String deviceId;
    private String deviceName;
    private String tenantId;
    private String userName;
    private String userAgent;
    private String deviceType;
    private String browserType;
    private String extensionVersion;
    private String ipAddress;
    private String location;
    private DeviceStatus status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private String osInfo;
    private String deviceFingerprint;
    private String deviceToken;
    private Boolean isAnonymous;
    private LocalDateTime linkedAt;
    private String linkedUserId;

    // Device User Info (auto-populated by database trigger)
    private String deviceUserId;
    private String deviceUserEmail;
    private String deviceUserStatus;
    private boolean linkedToPortalUser;

    /**
     * Convert Device entity to response DTO.
     */
    public static DeviceResponse fromEntity(Device device) {
        if (device == null) {
            return null;
        }
        return DeviceResponse.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .tenantId(device.getTenantId())
                .userName(device.getUserName())
                .userAgent(device.getUserAgent())
                .deviceType(device.getDeviceType())
                .browserType(device.getBrowserType())
                .extensionVersion(device.getExtensionVersion())
                .ipAddress(device.getIpAddress())
                .location(device.getLocation())
                .status(device.getStatus())
                .firstSeenAt(device.getFirstSeenAt())
                .lastSeenAt(device.getLastSeenAt())
                .osInfo(device.getOsInfo())
                .deviceFingerprint(device.getDeviceFingerprint())
                .deviceToken(device.getDeviceToken())
                .isAnonymous(device.getIsAnonymous())
                .linkedAt(device.getLinkedAt())
                .linkedUserId(device.getLinkedUserId())
                // Device User info
                .deviceUserId(device.getDeviceUser() != null ? device.getDeviceUser().getPkDeviceUserId() : null)
                .deviceUserEmail(device.getDeviceUser() != null ? device.getDeviceUser().getEmail() : null)
                .deviceUserStatus(device.getDeviceUser() != null ? device.getDeviceUser().getStatus() : null)
                .linkedToPortalUser(device.getDeviceUser() != null && device.getDeviceUser().getPortalUserId() != null)
                .build();
    }
}
