package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive session summary for a tenant.
 * Returned by GET /api/tenants/sessions/summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryDTO {

    /** Total number of active sessions across all users. */
    private int totalActiveSessions;

    /** Number of distinct users who have at least one active session. */
    private int uniqueActiveUsers;

    /** Active session count per Keycloak client ID. */
    private Map<String, Long> sessionsByClient;

    /** Average session duration in seconds (0 if no sessions). */
    private long avgSessionDurationSeconds;

    /** Human-readable average duration, e.g. "1h 23m". */
    private String avgSessionDurationFormatted;

    /** Timestamp when this summary was generated. */
    private LocalDateTime generatedAt;
}
