package com.secufusion.events.dto.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for API key validation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidateExtensionApiKeyResponse {

    private Boolean valid;
    private String tenantId;
    private String clientId;
    private String status;
    private String errorMessage;
    private String errorCode;
}
