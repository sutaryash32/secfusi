package com.secufusion.events.dto.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for token generation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "JWT token generation response with metadata")
public class GenerateTokenResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token expiry duration in seconds", example = "3600")
    private Long expiresIn;

    @Schema(description = "Token expiry timestamp", example = "2026-02-27T13:00:00")
    private LocalDateTime tokenExpiresAt;

    @Schema(description = "Tenant ID", example = "tenant-uuid-456")
    private String tenantId;

    @Schema(description = "API key expiry date", example = "2027-05-27T00:00:00")
    private LocalDateTime apiKeyExpiresAt;

    @Schema(description = "API key days remaining until expiry", example = "90")
    private Long apiKeyDaysRemaining;

    @Schema(description = "Success message", example = "Token generated successfully")
    private String message;

    @Schema(description = "Warning message if API key is expiring soon", example = "API key expires in 5 days")
    private String warningMessage;
}
