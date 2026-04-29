package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {

    private boolean success;

    private String message;

    private int sessionsRevoked;

    private LocalDateTime logoutTime;

    // Bulk operation fields
    @Builder.Default
    private List<String> revokedSessionIds = new ArrayList<>();

    @Builder.Default
    private List<String> failedSessionIds = new ArrayList<>();

    private int totalRequested;

    public static LogoutResponse success(String message) {
        return LogoutResponse.builder()
                .success(true)
                .message(message)
                .sessionsRevoked(1)
                .logoutTime(LocalDateTime.now())
                .build();
    }

    public static LogoutResponse success(String message, int sessionsRevoked) {
        return LogoutResponse.builder()
                .success(true)
                .message(message)
                .sessionsRevoked(sessionsRevoked)
                .logoutTime(LocalDateTime.now())
                .build();
    }

    public static LogoutResponse successBulk(String message, List<String> revokedSessionIds,
                                              List<String> failedSessionIds, int totalRequested) {
        return LogoutResponse.builder()
                .success(failedSessionIds == null || failedSessionIds.isEmpty())
                .message(message)
                .sessionsRevoked(revokedSessionIds != null ? revokedSessionIds.size() : 0)
                .logoutTime(LocalDateTime.now())
                .revokedSessionIds(revokedSessionIds != null ? revokedSessionIds : new ArrayList<>())
                .failedSessionIds(failedSessionIds != null ? failedSessionIds : new ArrayList<>())
                .totalRequested(totalRequested)
                .build();
    }

    public static LogoutResponse partialSuccess(String message, List<String> revokedSessionIds,
                                                 List<String> failedSessionIds) {
        return LogoutResponse.builder()
                .success(false)
                .message(message)
                .sessionsRevoked(revokedSessionIds != null ? revokedSessionIds.size() : 0)
                .logoutTime(LocalDateTime.now())
                .revokedSessionIds(revokedSessionIds != null ? revokedSessionIds : new ArrayList<>())
                .failedSessionIds(failedSessionIds != null ? failedSessionIds : new ArrayList<>())
                .totalRequested(
                        (revokedSessionIds != null ? revokedSessionIds.size() : 0) +
                        (failedSessionIds != null ? failedSessionIds.size() : 0))
                .build();
    }

    public static LogoutResponse failure(String message) {
        return LogoutResponse.builder()
                .success(false)
                .message(message)
                .sessionsRevoked(0)
                .logoutTime(LocalDateTime.now())
                .build();
    }
}
