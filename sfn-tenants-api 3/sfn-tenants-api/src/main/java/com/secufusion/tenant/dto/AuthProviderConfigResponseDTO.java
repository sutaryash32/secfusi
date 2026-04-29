package com.secufusion.tenant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthProviderConfigResponseDTO {

    private UUID authId;

    private String ssoType;
    private String issuerUri;
    private String authServerUrl;
    private String tokenEndpoint;
    private String jwkUri;

    private String clientId;

    private String redirectUri;
    private String loginUrl;

    private String scopes;
}
