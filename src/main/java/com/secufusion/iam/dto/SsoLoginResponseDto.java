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

    // Authorization status
    private boolean authorized;
    private String message;

    // User info from JWT claims
    private String email;
    private String preferredUsername;

    // SSO Configuration fields
    private String ssoConfigId;
    private String alias;
    private String providerId;
    private String tenantId;
    private Boolean enabled;
    private Boolean trustEmail;
    private Boolean storeToken;
    private Boolean linkOnly;
    private String displayName;
    private String clientId;
    private String authorizationUrl;
    private String tokenUrl;
    private String userInfoUrl;
    private String issuer;
    private String redirectUri;
    private Boolean setAsDefaultLogin;
    private String fkTenantId;
    private String active;
}
