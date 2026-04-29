package com.secufusion.events.dto.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for API key usage statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyUsageStatsResponse {

    private String apiKeyId;
    private String tenantId;
    private Integer totalUsers;
    private Integer activeUsers;
    private List<DeviceUserStats> users;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeviceUserStats {
        private String deviceUserId;
        private String email;
        private String userName;
        private String displayName;
        private String status;
        private Integer groupCount;
        private LocalDateTime lastSeenAt;
    }
}
