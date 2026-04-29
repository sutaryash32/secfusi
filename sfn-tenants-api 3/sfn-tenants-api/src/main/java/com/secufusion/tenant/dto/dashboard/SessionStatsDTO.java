package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Session statistics - visible to all tenant types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatsDTO {

    private long activeSessions;
    private long offlineSessions;
    private long totalSessionsToday;
    private long peakConcurrentSessions;
    private double averageSessionDuration; // in minutes

    // Session distribution by client
    private Map<String, Long> sessionsByClient;

    // For hierarchy views
    private long sessionsAcrossAllTenants;

    public static SessionStatsDTO forSingleTenant(long activeSessions, long offlineSessions,
            long totalSessionsToday, long peakConcurrentSessions, double averageSessionDuration,
            Map<String, Long> sessionsByClient) {
        return SessionStatsDTO.builder()
                .activeSessions(activeSessions)
                .offlineSessions(offlineSessions)
                .totalSessionsToday(totalSessionsToday)
                .peakConcurrentSessions(peakConcurrentSessions)
                .averageSessionDuration(averageSessionDuration)
                .sessionsByClient(sessionsByClient)
                .build();
    }

    public static SessionStatsDTO forHierarchy(long activeSessions, long offlineSessions,
            long totalSessionsToday, long peakConcurrentSessions, double averageSessionDuration,
            Map<String, Long> sessionsByClient, long sessionsAcrossAllTenants) {
        return SessionStatsDTO.builder()
                .activeSessions(activeSessions)
                .offlineSessions(offlineSessions)
                .totalSessionsToday(totalSessionsToday)
                .peakConcurrentSessions(peakConcurrentSessions)
                .averageSessionDuration(averageSessionDuration)
                .sessionsByClient(sessionsByClient)
                .sessionsAcrossAllTenants(sessionsAcrossAllTenants)
                .build();
    }
}
