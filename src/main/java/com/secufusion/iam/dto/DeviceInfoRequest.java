package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for device information sent from frontend during login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfoRequest {

    private String deviceFingerprint;
    private String deviceId;
    private String deviceName;
    private String browserType;
    private String osInfo;
}
