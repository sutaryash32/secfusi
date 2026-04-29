package com.secufusion.events.dto.apikey;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for validating an API key
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateExtensionApiKeyRequest {

    @NotBlank(message = "API key is required")
    private String apiKey;
}
