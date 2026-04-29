package com.secufusion.events.dto.apikey;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for rotating an Extension API Key
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotateExtensionApiKeyRequest {

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotBlank(message = "Rotation type is required")
    private String rotationType; // MANUAL, AUTOMATIC, COMPROMISED
}
