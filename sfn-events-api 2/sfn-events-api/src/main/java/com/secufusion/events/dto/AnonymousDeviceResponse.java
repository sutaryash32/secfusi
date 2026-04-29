package com.secufusion.events.dto;

import com.secufusion.events.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for anonymous device registration.
 * Contains the device token used for subsequent requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnonymousDeviceResponse {

    /**
     * Whether registration was successful
     */
    private Boolean success;

    /**
     * Response message
     */
    private String message;

    /**
     * Unique device token for subsequent requests.
     * Browser extension should store this securely.
     */
    private String deviceToken;

    /**
     * Device ID assigned by the system
     */
    private String deviceId;

    /**
     * Tenant ID the device is registered to
     */
    private String tenantId;

    /**
     * Device type detected from user agent
     */
    private String deviceType;

    /**
     * Browser type detected from user agent
     */
    private String browserType;

    /**
     * When the device was registered
     */
    private LocalDateTime registeredAt;

    /**
     * Sync interval in seconds (how often to sync extensions)
     */
    private Integer syncIntervalSeconds;

    /**
     * Whether the device is new (just registered) or existing
     */
    private Boolean isNewDevice;

    /**
     * Create response from device entity
     */
    public static AnonymousDeviceResponse fromEntity(Device device, boolean isNew) {
        return AnonymousDeviceResponse.builder()
                .success(true)
                .message(isNew ? "Device registered successfully" : "Device identified successfully")
                .deviceToken(device.getDeviceToken())
                .deviceId(device.getDeviceId())
                .tenantId(device.getTenantId())
                .deviceType(device.getDeviceType())
                .browserType(device.getBrowserType())
                .registeredAt(device.getFirstSeenAt())
                .syncIntervalSeconds(300) // Default 5 minutes
                .isNewDevice(isNew)
                .build();
    }

    /**
     * Create error response
     */
    public static AnonymousDeviceResponse error(String message) {
        return AnonymousDeviceResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
