package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for registering a new device or updating an existing one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {

    /**
     * Human-readable name for the device.
     */
    private String deviceName;

    /**
     * User agent string from the browser.
     */
    @NotBlank(message = "User agent is required")
    private String userAgent;

    /**
     * Version of the browser extension.
     */
    private String extensionVersion;

    /**
     * IP address of the device.
     */
    private String ipAddress;

    /**
     * Operating system information.
     */
    private String osInfo;

    /**
     * Unique fingerprint identifier from the extension.
     * Used to identify the same device across sessions.
     */
    private String deviceFingerprint;

    /**
     * Actual end-user email (required for APIKEY tenants).
     * APIKEY tokens are client_credentials grants whose preferred_username
     * is the Keycloak service-account name, not the real user.
     * The extension must send the user's email so the DeviceUser record
     * is created with the correct identity.
     */
    private String userEmail;

    /**
     * Display name of the end user (optional, for APIKEY tenants).
     */
    private String userDisplayName;
}
