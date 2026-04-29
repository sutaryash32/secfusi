package com.secufusion.events.dto.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Response DTO for Extension API Key (excludes sensitive data)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Extension API Key details (excludes raw key and sensitive credentials)")
public class ExtensionApiKeyResponse {

    @Schema(description = "Unique API key ID", example = "key-uuid-123")
    private String pkExtensionApiKeyId;

    @Schema(description = "Tenant ID", example = "tenant-uuid-456")
    private String tenantId;

    @Schema(description = "First 12 characters of API key (for display)", example = "sk_AbCdEfGh")
    private String keyPrefix;

    @Schema(description = "Keycloak client ID", example = "acme-corp-extension-client")
    private String clientId;

    private String rawKey;

    @Schema(description = "User-friendly name", example = "Production Extension Key")
    private String name;

    @Schema(description = "Description", example = "API key for production deployment")
    private String description;

    @Schema(description = "Owner email", example = "admin@example.com")
    private String ownerEmail;

    @Schema(description = "Status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "REVOKED", "EXPIRED"})
    private String status;

    @Schema(description = "Expiry date", example = "2027-05-27T00:00:00")
    private LocalDateTime expiresAt;

    @Schema(description = "Last used timestamp", example = "2026-02-27T12:00:00")
    private LocalDateTime lastUsedAt;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Created by user", example = "admin@example.com")
    private String createdBy;

    @Schema(description = "Last updated by user", example = "admin@example.com")
    private String updatedBy;

    // Computed fields
    @Schema(description = "Whether API key has expired", example = "false")
    private Boolean isExpired;

    @Schema(description = "Whether API key is expiring soon (within warning threshold)", example = "false")
    private Boolean isExpiringSoon;

    @Schema(description = "Days remaining until expiry", example = "90")
    private Long daysRemaining;
}
