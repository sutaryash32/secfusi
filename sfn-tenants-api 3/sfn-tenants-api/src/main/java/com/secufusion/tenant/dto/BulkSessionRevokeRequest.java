package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotEmpty;
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
public class BulkSessionRevokeRequest {

    @NotEmpty(message = "Session IDs list cannot be empty")
    @Size(max = 100, message = "Cannot revoke more than 100 sessions at once")
    private List<@Size(max = 100, message = "Session ID must not exceed 100 characters") String> sessionIds;
}
