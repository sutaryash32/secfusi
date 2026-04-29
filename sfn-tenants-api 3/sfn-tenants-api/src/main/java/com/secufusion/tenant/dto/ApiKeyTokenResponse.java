package com.secufusion.tenant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collection;

@Data
@Builder
public class ApiKeyTokenResponse {

    private boolean success;

    /** The tenant's realm/identifier — used by the client to direct future API calls */
    private String tenantName;

    /** Keycloak access token (JWT) */
    private String accessToken;

    /** Token type — always "Bearer" */
    private String tokenType;

    /** Seconds until the access token expires */
    private int expiresIn;

    /** Error message, present only when success=false */
    private String error;

    public static ApiKeyTokenResponse success(String tenantName, String accessToken,
                                              String tokenType, int expiresIn) {
        return ApiKeyTokenResponse.builder()
                .success(true)
                .tenantName(tenantName)
                .accessToken(accessToken)
                .tokenType(tokenType != null ? tokenType : "Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    public static ApiKeyTokenResponse failure(String error) {
        return null;
    }


}