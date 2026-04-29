package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @Size(max = 100, message = "Tenant name must not exceed 100 characters")
    private String tenantName;

    @Size(max = 100, message = "User ID must not exceed 100 characters")
    private String userId;

    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    private String sessionId;

    /**
     * List of session IDs for bulk revocation.
     */
    private List<@Size(max = 100, message = "Session ID must not exceed 100 characters") String> sessionIds;
}
