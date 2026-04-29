package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRefreshResponse {

    private boolean success;

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private int expiresIn;

    private int refreshExpiresIn;

    private String error;

    public static TokenRefreshResponse success(String accessToken, String refreshToken,
                                                int expiresIn, int refreshExpiresIn) {
        return TokenRefreshResponse.builder()
                .success(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .refreshExpiresIn(refreshExpiresIn)
                .build();
    }

    public static TokenRefreshResponse failure(String error) {
        return TokenRefreshResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
