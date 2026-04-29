package com.secufusion.events.dto.apikey;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for updating API key settings
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateApiKeySettingsRequest {

    @NotEmpty(message = "Settings cannot be empty")
    private Map<String, String> settings;
}
