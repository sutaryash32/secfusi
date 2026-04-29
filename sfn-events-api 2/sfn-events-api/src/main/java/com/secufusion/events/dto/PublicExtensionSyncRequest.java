package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for public (unauthenticated) extension sync.
 * Supports MSI, Intune, and email-based tenant identification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicExtensionSyncRequest {

    // ===== Tenant Identification (at least one required) =====

    /**
     * Tenant code for MSI/Intune deployments.
     * E.g., "ACME-2024", "CORP-MSI"
     */
    private String tenantCode;

    /**
     * User email for domain-based tenant lookup.
     * E.g., "user@acme.com" → extracts "acme.com" to find tenant
     */
    private String userEmail;

    // ===== Device Identification =====

    /**
     * Device token for returning devices (issued on first sync).
     */
    private String deviceToken;

    /**
     * Client-generated device fingerprint for deduplication.
     */
    private String deviceFingerprint;

    /**
     * Device name/hostname.
     */
    private String deviceName;

    // ===== Device Info =====

    /**
     * Browser type (Chrome, Edge, Firefox, etc.)
     */
    @NotBlank(message = "Browser type is required")
    private String browserType;

    /**
     * Browser version
     */
    private String browserVersion;

    /**
     * Operating system info
     */
    private String osInfo;

    /**
     * Device type (desktop, laptop, etc.)
     */
    private String deviceType;

    /**
     * SecuFusion extension version
     */
    private String extensionVersion;

    /**
     * User agent string
     */
    private String userAgent;

    // ===== Extension Data =====

    /**
     * List of installed extensions
     */
    @NotNull(message = "Extensions list is required")
    private List<ExtensionSyncRequest.ExtensionInfo> extensions;

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
     * Check if this is a returning device (has token).
     */
    public boolean isReturningDevice() {
        return deviceToken != null && !deviceToken.isBlank();
    }

    /**
     * Check if tenant can be identified.
     */
    public boolean hasTenantIdentifier() {
        return (tenantCode != null && !tenantCode.isBlank()) ||
               (userEmail != null && userEmail.contains("@"));
    }
}
