package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event DTO for device registration.
 * Published by IAM API when a user logs in with device info.
 * Consumed by Events API to sync device data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationEvent {

    private String eventId;
    private String tenantId;
    private String userId;
    private String userName;
    private String deviceFingerprint;
    private String deviceId;
    private String deviceName;
    private String browserType;
    private String osInfo;
    private String userAgent;
    private String ipAddress;
    private String location;
    private String extensionVersion;
    private long loginTimestamp;
    private String loginType; // WEBSITE, EXTENSION
}
