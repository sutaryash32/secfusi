package com.secufusion.events.dto.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for token generation with validation steps
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenValidationStepResponse {

    private Boolean success;

    // Step 1: Validate API Key
    private StepResult step1ValidateApiKey;

    // Step 2: Check Expiry
    private StepResult step2CheckExpiry;

    // Step 3: Generate Token
    private StepResult step3GenerateToken;

    private GenerateTokenResponse tokenData;
    private String errorMessage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepResult {
        private Boolean success;
        private String message;
        private String error;
    }
}
