package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.LoggedInUserDetailsBean;
import com.secufusion.tenant.dto.dashboard.*;
import com.secufusion.tenant.exception.AccessDeniedException;
import com.secufusion.tenant.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Dashboard REST Controller.
 * Provides role-based dashboard statistics for all tenant types.
 *
 * - PLATFORM_ADMIN: Full access to all data without limitations (tenant with no parent)
 * - MASTER_MSSP: Sees platform-wide stats (all MSSPs and Enterprises)
 * - MSSP: Sees their own enterprises' statistics
 * - ENTERPRISE: Sees only their own organization's statistics
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/dashboard")
@Tag(name = "Dashboard", description = "Dashboard statistics APIs - role-based views")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    // ============================================================
    // OVERVIEW ENDPOINTS
    // ============================================================

    @GetMapping("/overview")
    @Operation(summary = "Get dashboard overview",
            description = "Returns complete dashboard based on tenant type (MASTER_MSSP, MSSP, or ENTERPRISE)")
    public ResponseEntity<DashboardOverviewDTO> getDashboardOverview(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/overview", tenantId);
        DashboardOverviewDTO dashboard = dashboardService.getDashboardOverview(tenantId);
        return ResponseEntity.ok(dashboard);
    }

    // ============================================================
    // INDIVIDUAL STAT ENDPOINTS
    // ============================================================

    @GetMapping("/stats/tenants")
    @Operation(summary = "Get tenant statistics",
            description = "Returns tenant hierarchy stats. Available to MASTER_MSSP and MSSP only.")
    public ResponseEntity<TenantStatsDTO> getTenantStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/tenants", tenantId);
        TenantStatsDTO stats = dashboardService.getTenantStats(tenantId);
        if (stats == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/users")
    @Operation(summary = "Get user statistics",
            description = "Returns user statistics for the tenant")
    public ResponseEntity<UserStatsDTO> getUserStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/users", tenantId);
        UserStatsDTO stats = dashboardService.getUserStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/sessions")
    @Operation(summary = "Get session statistics",
            description = "Returns active session statistics for the tenant")
    public ResponseEntity<SessionStatsDTO> getSessionStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/sessions", tenantId);
        SessionStatsDTO stats = dashboardService.getSessionStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/logins")
    @Operation(summary = "Get login statistics",
            description = "Returns login statistics including trends and failure analysis")
    public ResponseEntity<LoginStatsDTO> getLoginStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/logins", tenantId);
        LoginStatsDTO stats = dashboardService.getLoginStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/security")
    @Operation(summary = "Get security statistics",
            description = "Returns security statistics including brute force and MFA metrics")
    public ResponseEntity<SecurityStatsDTO> getSecurityStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/security", tenantId);
        SecurityStatsDTO stats = dashboardService.getSecurityStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/policies")
    @Operation(summary = "Get policy statistics",
            description = "Returns browser policy and user group statistics")
    public ResponseEntity<PolicyStatsDTO> getPolicyStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/policies", tenantId);
        PolicyStatsDTO stats = dashboardService.getPolicyStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/licenses")
    @Operation(summary = "Get license statistics",
            description = "Returns license usage and allocation stats. Available to MASTER_MSSP and MSSP only.")
    public ResponseEntity<LicenseStatsDTO> getLicenseStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/licenses", tenantId);
        LicenseStatsDTO stats = dashboardService.getLicenseStats(tenantId);
        if (stats == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(stats);
    }

    // ============================================================
    // DATE RANGE STATS
    // ============================================================

    @GetMapping("/stats/range")
    @Operation(
            summary = "Get stats for a custom date range",
            description = """
                    Returns login, security, audit, and browser extension event statistics for a
                    caller-specified date range.

                    **Parameters**
                    - `startDate` – inclusive start date (ISO format: `YYYY-MM-DD`)
                    - `endDate`   – inclusive end date   (ISO format: `YYYY-MM-DD`)

                    **Rules**
                    - `startDate` must be ≤ `endDate`
                    - Maximum range: 366 days

                    **Role-based scoping** is identical to all other dashboard endpoints:
                    PLATFORM_ADMIN sees all-tenant aggregates; everyone else sees their own tenant's data.
                    """)
    public ResponseEntity<DateRangeStatsDTO> getDateRangeStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId,
            @Parameter(description = "Inclusive start date (YYYY-MM-DD)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Inclusive end date (YYYY-MM-DD)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/stats/range?startDate={}&endDate={}",
                tenantId, startDate, endDate);
        DateRangeStatsDTO stats = dashboardService.getDateRangeStats(tenantId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    // ============================================================
    // TENANT DASHBOARD STATS
    // ============================================================

    @GetMapping("/tenant-stats")
    @Operation(summary = "Get tenant dashboard statistics",
            description = "Returns active devices, total events, security events, organization stats, recent users, and recent devices")
    public ResponseEntity<TenantDashboardStatsDTO> getTenantDashboardStats(
            HttpServletRequest request,
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        validateTenantAccess(request, tenantId);
        log.info("GET /api/tenants/{}/dashboard/tenant-stats", tenantId);
        TenantDashboardStatsDTO stats = dashboardService.getTenantDashboardStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    // ============================================================
    // AUTHORIZATION HELPER
    // ============================================================

    /**
     * Validates that the authenticated user has access to the requested tenant's dashboard.
     * Access is allowed if:
     * - The user belongs to the requested tenant (same tenantId)
     * - The user is a parent tenant (MSSP viewing child enterprise, or PLATFORM_ADMIN/MASTER_MSSP)
     */
    private void validateTenantAccess(HttpServletRequest request, String requestedTenantId) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");

        if (loggedInUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        String callerTenantId = loggedInUser.getTenantId();

        // Same tenant — always allowed
        if (callerTenantId.equals(requestedTenantId)) {
            return;
        }

        // Parent tenant access is checked via service layer (hierarchy validation)
        if (!dashboardService.canAccessTenantDashboard(callerTenantId, requestedTenantId)) {
            log.warn("Tenant access denied: caller={} requested={}", callerTenantId, requestedTenantId);
            throw new AccessDeniedException("You do not have access to this tenant's dashboard");
        }
    }
}
