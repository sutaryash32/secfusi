package com.secufusion.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSearchRequest {

    @Size(max = 100, message = "User ID must not exceed 100 characters")
    private String userId;

    @Size(max = 255, message = "Username must not exceed 255 characters")
    private String username;

    @Size(max = 45, message = "IP address must not exceed 45 characters")
    private String ipAddress;

    @Size(max = 100, message = "Client ID must not exceed 100 characters")
    private String clientId;

    private LocalDateTime startTimeFrom;

    private LocalDateTime startTimeTo;

    private LocalDateTime lastAccessFrom;

    private LocalDateTime lastAccessTo;

    private Boolean rememberMe;

    @Min(value = 0, message = "Page number must be non-negative")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    @Builder.Default
    private int size = 50;

    @Size(max = 50, message = "Sort by field must not exceed 50 characters")
    @Builder.Default
    private String sortBy = "lastAccess";

    @Builder.Default
    private boolean sortDesc = true;
}
