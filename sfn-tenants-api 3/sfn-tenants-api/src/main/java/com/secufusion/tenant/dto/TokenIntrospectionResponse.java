package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospectionResponse {

    private boolean active;

    private String tokenType;

    private String clientId;

    private String username;

    private String userId;

    private List<String> scope;

    private LocalDateTime expiresAt;

    private LocalDateTime issuedAt;

    private String issuer;

    private String subject;

    private String audience;

    private String sessionId;

    private String error;

    public static TokenIntrospectionResponse active(String clientId, String username, String userId,
                                                     List<String> scope, LocalDateTime expiresAt,
                                                     LocalDateTime issuedAt, String sessionId) {
        return TokenIntrospectionResponse.builder()
                .active(true)
                .tokenType("Bearer")
                .clientId(clientId)
                .username(username)
                .userId(userId)
                .scope(scope)
                .expiresAt(expiresAt)
                .issuedAt(issuedAt)
                .sessionId(sessionId)
                .build();
    }

    public static TokenIntrospectionResponse inactive() {
        return TokenIntrospectionResponse.builder()
                .active(false)
                .build();
    }

    public static TokenIntrospectionResponse error(String error) {
        return TokenIntrospectionResponse.builder()
                .active(false)
                .error(error)
                .build();
    }
}
