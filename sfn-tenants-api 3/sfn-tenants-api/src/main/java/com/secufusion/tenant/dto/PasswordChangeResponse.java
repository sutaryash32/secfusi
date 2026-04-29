package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeResponse {

    private boolean success;

    private String message;

    private LocalDateTime changedAt;

    private boolean requiresRelogin;

    public static PasswordChangeResponse success(String message) {
        return PasswordChangeResponse.builder()
                .success(true)
                .message(message)
                .changedAt(LocalDateTime.now())
                .requiresRelogin(true)
                .build();
    }

    public static PasswordChangeResponse success(String message, boolean requiresRelogin) {
        return PasswordChangeResponse.builder()
                .success(true)
                .message(message)
                .changedAt(LocalDateTime.now())
                .requiresRelogin(requiresRelogin)
                .build();
    }

    public static PasswordChangeResponse failure(String message) {
        return PasswordChangeResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
