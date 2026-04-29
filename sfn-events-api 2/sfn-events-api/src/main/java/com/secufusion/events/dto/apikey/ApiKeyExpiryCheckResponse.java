package com.secufusion.events.dto.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for checking API key expiry
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyExpiryCheckResponse {

    private Boolean valid;
    private String keyId;
    private String status;
    private LocalDateTime expiresAt;
    private Long daysRemaining;
    private Boolean expiringSoon;
    private String warningMessage;
}
