package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard statistics for a caller-specified date range.
 *
 * Returned by: GET /api/tenants/{tenantId}/dashboard/stats/range
 *   ?startDate=2026-01-01&endDate=2026-01-31
 *
 * All counts cover the closed interval [startDate 00:00, endDate 23:59:59].
 * Role-based scoping is identical to the other dashboard endpoints:
 *   PLATFORM_ADMIN  → aggregated across all tenants
 *   MASTER_MSSP / MSSP / ENTERPRISE → scoped to the requesting tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateRangeStatsDTO {

    // ── Requested range (echoed back for clarity) ───────────────────────────
    private LocalDate startDate;
    private LocalDate endDate;
    /** Number of calendar days in the range (inclusive). */
    private long totalDays;

    // ── Login stats ──────────────────────────────────────────────────────────
    private long totalLogins;
    private long successfulLogins;
    private long failedLogins;
    /** Successful logins as a percentage of total logins (0–100). */
    private double successRate;
    /** One entry per calendar day in the range. */
    private List<LoginStatsDTO.DailyLoginTrend> dailyLoginTrends;
    /** Logins by hour of day (key = 0–23). Useful for peak-hour analysis. */
    private Map<Integer, Long> loginsByHour;
    /** Failed-login error codes and their counts within the range. */
    private Map<String, Long> failureReasons;

    // ── Security / audit stats ───────────────────────────────────────────────
    /** Total security events (LOGIN_FAILURE, ACCOUNT_LOCKED, MFA_FAILURE, …). */
    private long securityEvents;
    /** Security event breakdown by type. */
    private Map<String, Long> securityEventsByType;
    /** Number of ACCOUNT_LOCKED events in the range. */
    private long accountLockedEvents;
    /** Distinct users who authenticated with MFA in the range. */
    private long mfaUsers;

    // ── General audit event stats ────────────────────────────────────────────
    /** All audit events recorded in the range (logins + management events). */
    private long totalAuditEvents;
    /** Unique devices (by deviceInfo) that appeared in the range. */
    private long activeDevices;
    /** Device type breakdown (Desktop, Mobile, Tablet, Unknown). */
    private Map<String, Long> devicesByType;
    /** Audit event counts grouped by originating service. */
    private Map<String, Long> auditEventsBySourceService;

    // ── Browser extension event stats ────────────────────────────────────────
    /** Total browser extension events captured in the range. */
    private long browserEvents;
    /** Browser events that violated a policy in the range. */
    private long policyViolations;
    /** Browser events that were blocked in the range. */
    private long blockedBrowserEvents;
    /** Browser events classified as security events in the range. */
    private long browserSecurityEvents;

    // ── Meta ─────────────────────────────────────────────────────────────────
    private LocalDateTime generatedAt;

    public static DateRangeStatsDTO empty(LocalDate startDate, LocalDate endDate, long totalDays) {
        return DateRangeStatsDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalDays(totalDays)
                .totalLogins(0)
                .successfulLogins(0)
                .failedLogins(0)
                .successRate(0.0)
                .securityEvents(0)
                .accountLockedEvents(0)
                .mfaUsers(0)
                .totalAuditEvents(0)
                .activeDevices(0)
                .browserEvents(0)
                .policyViolations(0)
                .blockedBrowserEvents(0)
                .browserSecurityEvents(0)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
