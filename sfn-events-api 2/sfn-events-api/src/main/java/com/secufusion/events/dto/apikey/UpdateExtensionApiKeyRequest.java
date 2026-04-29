package com.secufusion.events.dto.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for updating an existing Extension API Key
 *
 * All fields are optional - only provided fields will be updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update an Extension API Key. All fields are optional - only provided fields will be updated.")
public class UpdateExtensionApiKeyRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(
        description = "New name for the API key",
        example = "Updated Production Key",
        maxLength = 100
    )
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(
        description = "New description for the API key",
        example = "Updated description with new purpose",
        maxLength = 500
    )
    private String description;

    @Schema(
        description = "New expiry date for the API key. Must be in the future.",
        example = "2027-12-31T23:59:59"
    )
    private LocalDateTime expiresAt;

    @Schema(
        description = "New status for the API key. Valid values: ACTIVE, INACTIVE. " +
                      "Note: REVOKED and EXPIRED statuses cannot be set manually - use dedicated endpoints.",
        allowableValues = {"ACTIVE", "INACTIVE"},
        example = "ACTIVE"
    )
    private String status;
}
