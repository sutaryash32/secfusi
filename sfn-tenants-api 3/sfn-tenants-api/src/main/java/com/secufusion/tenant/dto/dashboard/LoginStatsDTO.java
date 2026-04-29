package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Login statistics - visible to all tenant types.
 *
 * DATA WINDOWS:
 *   loginsToday / successfulLoginsToday / failedLoginsToday / successRateToday
 *     → Since midnight (today, 00:00 local time)
 *
 *   loginsThisWeek / successfulLoginsThisWeek / failedLoginsThisWeek
 *     → Last 7 days (rolling window from midnight today)
 *
 *   loginsThisMonth / successfulLoginsThisMonth / failedLoginsThisMonth
 *     → Last 30 days (rolling window from midnight today)
 *
 *   dailyTrends
 *     → Last 7 days (one entry per calendar day)
 *
 *   loginsByHour
 *     → Today only (since midnight); map key = hour of day (0–23)
 *
 *   failureReasons
 *     → Last 7 days; map key = failure reason string, value = count
 *
 *   loginsAcrossAllTenants
 *     → Last 30 days, aggregated across the hierarchy (same window as loginsThisMonth)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginStatsDTO {

    // Today's stats (since midnight)
    private long loginsToday;
    private long successfulLoginsToday;
    private long failedLoginsToday;
    private double successRateToday;

    // Last 7 days (rolling)
    private long loginsThisWeek;
    private long successfulLoginsThisWeek;
    private long failedLoginsThisWeek;

    // Last 30 days (rolling)
    private long loginsThisMonth;
    private long successfulLoginsThisMonth;
    private long failedLoginsThisMonth;

    // Daily breakdown — last 7 days (one entry per day)
    private List<DailyLoginTrend> dailyTrends;

    // Hourly distribution — today only (key = 0–23)
    private Map<Integer, Long> loginsByHour;

    // Top failure reasons — last 7 days
    private Map<String, Long> failureReasons;

    // Hierarchy aggregate — last 30 days
    private long loginsAcrossAllTenants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyLoginTrend {
        private String date;
        private long totalLogins;
        private long successfulLogins;
        private long failedLogins;
    }

    public static LoginStatsDTO empty() {
        return LoginStatsDTO.builder()
                .loginsToday(0)
                .successfulLoginsToday(0)
                .failedLoginsToday(0)
                .successRateToday(0.0)
                .loginsThisWeek(0)
                .successfulLoginsThisWeek(0)
                .failedLoginsThisWeek(0)
                .loginsThisMonth(0)
                .successfulLoginsThisMonth(0)
                .failedLoginsThisMonth(0)
                .build();
    }
}
