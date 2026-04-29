package com.secufusion.tenant.dto;

import lombok.Data;

@Data
public class AuthProviderConfigDTO {

    private String ssoType;
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
