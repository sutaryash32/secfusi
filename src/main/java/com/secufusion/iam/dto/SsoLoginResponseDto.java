package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoLoginResponseDto {

    private boolean authorized;
    private String message;
    private String username;
    private String preferredUsername;
    private String tenantName;
    private String alias;
}
