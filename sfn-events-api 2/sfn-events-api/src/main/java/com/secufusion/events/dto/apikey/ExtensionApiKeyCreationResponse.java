package com.secufusion.events.dto.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for API key creation (includes raw key - shown only once)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionApiKeyCreationResponse {

    private ExtensionApiKeyResponse key;

    /**
     * Raw API key - ONLY returned during creation
     * MUST be stored securely by the client
     */
    private String rawKey;

    private String warning;
}
