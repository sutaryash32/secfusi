package com.secufusion.events.dto.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for API key settings/configuration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeySettingsResponse {

    private Map<String, String> settings;
}
