package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for registering an anonymous device before user login.
 * Used by browser extension for pre-login extension sync.
 * Supports MSI/Intune (tenantCode) and email-based (userEmail) tenant identification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnonymousDeviceRegistrationRequest {

    /**
     * Tenant code from MSI/Intune deployment (e.g., "ACME-2024").
     * Use this OR userEmail to identify the tenant.
     */
    private String tenantCode;

    /**
     * User email for domain-based tenant lookup (e.g., "user@acme.com").
     * Use this OR tenantCode to identify the tenant.
     */
    private String userEmail;

    /**
     * User agent string from the browser.
     */
    @NotBlank(message = "User agent is required")
    private String userAgent;

    /**
     * Unique fingerprint identifier from the extension.
     * Used to identify the same device across sessions.
     */
    private String deviceFingerprint;

    /**
     * Version of the SecuFusion browser extension.
     */
    private String extensionVersion;

    /**
     * IP address of the device (optional, can be extracted from request).
     */
    private String ipAddress;

    /**
     * Operating system information.
     */
    private String osInfo;

    /**
     * Human-readable name for the device (optional).
     */
    private String deviceName;

    /**
     * Get domain from user email.
     */
    public String getEmailDomain() {
        if (userEmail != null && userEmail.contains("@")) {
            return userEmail.substring(userEmail.indexOf("@") + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Check if tenant can be identified.
     */
    public boolean hasTenantIdentifier() {
        return (tenantCode != null && !tenantCode.isBlank()) ||
               (userEmail != null && userEmail.contains("@"));
    }
}
