package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Security statistics - visible to all tenant types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityStatsDTO {

    // Brute force protection stats
    private long blockedIPs;
    private long temporarilyLockedUsers;
    private long permanentlyLockedUsers;
    private long bruteForceAttemptsToday;

    // MFA stats
    private long mfaEnabledUsers;
    private long mfaDisabledUsers;
    private double mfaAdoptionRate;

    // Password policy stats
    private long usersWithExpiredPasswords;
    private long usersWithExpiringPasswords; // Expiring in next 7 days

    // Security events
    private long suspiciousLoginAttempts;
    private long unknownDeviceLogins;
    private long newLocationLogins;

    // Top blocked IPs
    private List<String> topBlockedIPs;

    // Security events by type
    private Map<String, Long> securityEventsByType;

    // For hierarchy views
    private long securityEventsAcrossAllTenants;

    public static SecurityStatsDTO empty() {
        return SecurityStatsDTO.builder()
                .blockedIPs(0)
                .temporarilyLockedUsers(0)
                .permanentlyLockedUsers(0)
                .bruteForceAttemptsToday(0)
                .mfaEnabledUsers(0)
                .mfaDisabledUsers(0)
                .mfaAdoptionRate(0.0)
                .usersWithExpiredPasswords(0)
                .usersWithExpiringPasswords(0)
                .suspiciousLoginAttempts(0)
                .unknownDeviceLogins(0)
                .newLocationLogins(0)
                .build();
    }
}
