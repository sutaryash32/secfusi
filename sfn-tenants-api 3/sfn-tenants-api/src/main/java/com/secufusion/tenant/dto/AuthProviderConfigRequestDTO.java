package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthProviderConfigRequestDTO {

    @NotBlank
    private String ssoType;   // KEYCLOAK / OKTA / AUTH0 / CUSTOM

    private String issuerUri;
    private String authServerUrl;
    private String tokenEndpoint;
    private String jwkUri;

    private String clientId;
    private String clientSecret;

    private String redirectUri;
    private String loginUrl;

    private String scopes;
}
