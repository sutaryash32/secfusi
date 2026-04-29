package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAuditStatsDTO {

    private long totalLoginAttempts;
    private long successfulLogins;
    private long failedLogins;
    private long uniqueActiveUsers;
    private long totalLogouts;
    private long passwordResets;
    private long accountLockouts;
    private double loginSuccessRate;
    private Map<String, Long> eventCountsByType;
    private Map<Integer, Long> loginsByHour;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    public static LoginAuditStatsDTO empty(LocalDateTime start, LocalDateTime end) {
        return LoginAuditStatsDTO.builder()
                .totalLoginAttempts(0)
                .successfulLogins(0)
                .failedLogins(0)
                .uniqueActiveUsers(0)
                .totalLogouts(0)
                .passwordResets(0)
                .accountLockouts(0)
                .loginSuccessRate(0.0)
                .eventCountsByType(Map.of())
                .loginsByHour(Map.of())
                .periodStart(start)
                .periodEnd(end)
                .build();
    }
}
