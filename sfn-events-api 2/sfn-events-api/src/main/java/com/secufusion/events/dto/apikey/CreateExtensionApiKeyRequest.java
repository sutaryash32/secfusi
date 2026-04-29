package com.secufusion.events.dto.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new Extension API Key
 *
 * Simplified request - only requires name and description.
 * All other fields (tenant, owner, expiry, client credentials) are auto-resolved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new Extension API Key. Only name and description are required - all other fields are auto-resolved from user context.")
public class CreateExtensionApiKeyRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(
        description = "User-friendly name for the API key (e.g., 'Production Extension Key', 'Dev Environment')",
        example = "Production Extension Key",
        required = true,
        maxLength = 100
    )
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(
        description = "Optional description explaining the purpose of this API key",
        example = "API key for production browser extension deployment",
        maxLength = 500
    )
    private String description;

    // All other fields are auto-resolved:
    // - tenantId: extracted from authenticated user's JWT token (sub claim or tenant_id claim)
    // - clientId: auto-generated as "{tenant-name}-extension-client"
    // - clientSecret: auto-fetched from Keycloak or created if new tenant
    // - ownerEmail: extracted from X-User-Email header or JWT email claim
    // - expiresAt: set to current_date + DEFAULT_EXPIRY_DAYS (90 days by default)
}
