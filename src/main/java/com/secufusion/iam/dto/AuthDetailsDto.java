package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class AuthDetailsDto {
    private String tenantId;
    private String tenantKey;
    private String name;
    private String tenantType;
    private String keycloakUrl;
    private String realm;
    private String clientId;
    private String issuer;
    private String jwkUri;
    private String tokenUri;
    private String domain;
    private String status;
}
