package com.secufusion.events.dto.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for API key statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyStatisticsResponse {

    private Long totalKeys;
    private Long activeKeys;
    private Long inactiveKeys;
    private Long revokedKeys;
    private Long expiredKeys;
    private Long expiringSoonKeys;
}
