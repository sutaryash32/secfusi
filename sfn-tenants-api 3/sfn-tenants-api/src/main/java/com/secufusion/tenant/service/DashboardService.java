package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.dashboard.*;
import com.secufusion.tenant.entity.BrowserDevice;
import com.secufusion.tenant.entity.BrowserDeviceStatus;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.TenantTypeEnum;
import com.secufusion.tenant.exception.GlobalException;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.util.ResponseCodes;
import com.secufusion.tenant.repository.BrowserDeviceRepository;
import com.secufusion.tenant.repository.BrowserEventRepository;
import com.secufusion.tenant.repository.BrowserPolicyRepository;
import com.secufusion.tenant.repository.ExtensionPolicyRepository;
import com.secufusion.tenant.repository.GroupsRepository;
import com.secufusion.tenant.repository.LoginAuditRepository;
import com.secufusion.tenant.repository.NetworkPolicyRepository;
import com.secufusion.tenant.repository.EventsGroupRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import com.secufusion.tenant.repository.RolesRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard service providing role-based dashboard statistics.
 * Adapts data based on tenant type (PLATFORM_ADMIN, MASTER_MSSP, MSSP, ENTERPRISE).
 *
 * PLATFORM_ADMIN: Tenant with no parent (parentTenantId is null) - has full access to all data without limitations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TenantRepository tenantRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final UserRepository userRepository;
    private final GroupsRepository groupsRepository;
    private final RolesRepository rolesRepository;
    private final AuthService authService;
    private final BrowserEventRepository browserEventRepository;
    private final BrowserDeviceRepository browserDeviceRepository;
    private final BrowserPolicyRepository browserPolicyRepository;
    private final ExtensionPolicyRepository extensionPolicyRepository;
    private final NetworkPolicyRepository networkPolicyRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final EventsGroupRepository eventsGroupRepository;

    /**
     * Check if a caller tenant can access another tenant's dashboard.
     * Allowed when caller is a platform admin or a direct parent of the requested tenant.
     */
    public boolean canAccessTenantDashboard(String callerTenantId, String requestedTenantId) {
        Tenant callerTenant = tenantRepository.findByTenantID(callerTenantId).orElse(null);
        if (callerTenant == null) return false;

        // Platform admin can access any tenant's dashboard
        if (isPlatformAdmin(callerTenant)) return true;

        // Check if caller is a direct parent of the requested tenant
        Tenant requestedTenant = tenantRepository.findByTenantID(requestedTenantId).orElse(null);
        if (requestedTenant == null) return false;

        return callerTenantId.equals(requestedTenant.getParentTenantId());
    }

    /**
     * Get complete dashboard overview based on tenant type.
     * If parentTenantId is null, the tenant is treated as PLATFORM_ADMIN with full access.
     * Cached for 5 minutes per tenantId to reduce DB load.
     */
    @Cacheable(value = "dashboardOverview", key = "#tenantId")
    public DashboardOverviewDTO getDashboardOverview(String tenantId) {
        log.info("Getting dashboard overview for tenantId={}", tenantId);

        Tenant tenant = getTenant(tenantId);

        // Check if this is a Platform Admin (no parent tenant)
        if (isPlatformAdmin(tenant)) {
            log.info("Tenant {} is PLATFORM_ADMIN (no parent), providing full access", tenantId);
            return buildPlatformAdminDashboard(tenant);
        }

        TenantTypeEnum tenantType = TenantTypeEnum.fromCode(tenant.getTenantType());

        DashboardOverviewDTO dashboard;

        switch (tenantType) {
            case MASTER_MSSP:
                dashboard = buildMasterMsspDashboard(tenant);
                break;
            case MSSP:
                dashboard = buildMsspDashboard(tenant);
                break;
            case ENTERPRISE:
            default:
                dashboard = buildEnterpriseDashboard(tenant);
                break;
        }

        // For selfManaged MSSP/Master MSSP: expose flag + own-org stats so UI can render
        // the mode switcher and "My Organization" panel.
        if (Boolean.TRUE.equals(tenant.getSelfManaged())) {
            dashboard.setSelfManaged(true);
            dashboard.setMyOrgStats(getMyOrgStats(tenant.getTenantID()));
        }

        return dashboard;
    }

    /**
     * Check if tenant is a Platform Admin.
     * Requires BOTH no parent tenant AND PLATFORM_ADMIN tenant type to prevent
     * misconfigured tenants from gaining platform-level access.
     */
    private boolean isPlatformAdmin(Tenant tenant) {
        if (tenant.getParentTenantId() != null && !tenant.getParentTenantId().isEmpty()) {
            return false;
        }
        String type = tenant.getTenantType();
        return type != null && "PLATFORM_ADMIN".equalsIgnoreCase(type.trim());
    }

    /**
     * Build dashboard for Platform Admin - full access to all data without limitations.
     */
    private DashboardOverviewDTO buildPlatformAdminDashboard(Tenant tenant) {
        DashboardOverviewDTO dashboard = DashboardOverviewDTO.forPlatformAdmin(
                tenant.getTenantID(), tenant.getTenantName());

        // Tenant stats - can see all MSSPs and Enterprises (same as Master MSSP)
        dashboard.setTenantStats(getTenantStatsForPlatformAdmin());

        // User stats - aggregated across all tenants
        dashboard.setUserStats(getUserStatsForPlatformAdmin());

        // Session stats - own realm (avoids N+1 Keycloak calls across all tenants)
        dashboard.setSessionStats(getSessionStatsForPlatformAdmin(tenant));

        // Login stats - aggregated across all tenants
        dashboard.setLoginStats(getLoginStatsForPlatformAdmin());

        // Security stats - aggregated across all tenants
        dashboard.setSecurityStats(getSecurityStatsForPlatformAdmin());

        // Policy stats - aggregated across all tenants
        dashboard.setPolicyStats(getPolicyStatsForPlatformAdmin());

        // License stats - platform-wide
        dashboard.setLicenseStats(getLicenseStatsForPlatformAdmin());

        // Device & browser event stats - aggregated across all tenants
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeek = startOfDay.minusDays(7);
        LocalDateTime startOfMonth = startOfDay.minusDays(30);
        dashboard.setDeviceStats(getBrowserEventStatsForPlatformAdmin(startOfDay, startOfWeek, startOfMonth, now));

        return dashboard;
    }

    /**
     * Build dashboard for Master MSSP - sees its own tree of MSSPs and Enterprises.
     */
    private DashboardOverviewDTO buildMasterMsspDashboard(Tenant tenant) {
        DashboardOverviewDTO dashboard = DashboardOverviewDTO.forMasterMssp(
                tenant.getTenantID(), tenant.getTenantName());

        // Tenant stats - scoped to this MASTER_MSSP's tree
        dashboard.setTenantStats(getTenantStatsForMasterMssp(tenant.getTenantID()));

        // User stats - own users + usersAcrossAllTenants for hierarchy
        dashboard.setUserStats(getUserStatsForMasterMssp(tenant));

        // Session stats
        dashboard.setSessionStats(getSessionStatsForTenant(tenant));

        // Login stats
        dashboard.setLoginStats(getLoginStatsForTenant(tenant.getTenantID()));

        // Security stats
        dashboard.setSecurityStats(getSecurityStatsForTenant(tenant.getTenantID()));

        // Policy stats
        dashboard.setPolicyStats(getPolicyStatsForTenant(tenant.getTenantID()));

        // License stats
        dashboard.setLicenseStats(getLicenseStatsForMasterMssp());

        // Device & browser event stats
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeek = startOfDay.minusDays(7);
        LocalDateTime startOfMonth = startOfDay.minusDays(30);
        dashboard.setDeviceStats(getBrowserEventStats(tenant.getTenantID(), startOfDay, startOfWeek, startOfMonth, now));

        return dashboard;
    }

    /**
     * Build dashboard for MSSP - sees their own enterprises.
     */
    private DashboardOverviewDTO buildMsspDashboard(Tenant tenant) {
        DashboardOverviewDTO dashboard = DashboardOverviewDTO.forMssp(
                tenant.getTenantID(), tenant.getTenantName());

        // Tenant stats - can see their enterprises
        dashboard.setTenantStats(getTenantStatsForMssp(tenant.getTenantID()));

        // User stats - own users + usersAcrossAllTenants across child enterprises
        dashboard.setUserStats(getUserStatsForMssp(tenant));

        // Session stats
        dashboard.setSessionStats(getSessionStatsForTenant(tenant));

        // Login stats
        dashboard.setLoginStats(getLoginStatsForTenant(tenant.getTenantID()));

        // Security stats
        dashboard.setSecurityStats(getSecurityStatsForTenant(tenant.getTenantID()));

        // Policy stats
        dashboard.setPolicyStats(getPolicyStatsForTenant(tenant.getTenantID()));

        // License stats
        dashboard.setLicenseStats(getLicenseStatsForMssp(tenant));

        // Device & browser event stats
        LocalDateTime nowM = LocalDateTime.now();
        LocalDateTime startOfDayM = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeekM = startOfDayM.minusDays(7);
        LocalDateTime startOfMonthM = startOfDayM.minusDays(30);
        dashboard.setDeviceStats(getBrowserEventStats(tenant.getTenantID(), startOfDayM, startOfWeekM, startOfMonthM, nowM));

        return dashboard;
    }

    /**
     * Build dashboard for Enterprise - sees only their own data.
     */
    private DashboardOverviewDTO buildEnterpriseDashboard(Tenant tenant) {
        DashboardOverviewDTO dashboard = DashboardOverviewDTO.forEnterprise(
                tenant.getTenantID(), tenant.getTenantName());

        // No tenant stats for Enterprise (they don't manage other tenants)
        dashboard.setTenantStats(null);

        // User stats
        dashboard.setUserStats(getUserStatsForTenant(tenant));

        // Session stats
        dashboard.setSessionStats(getSessionStatsForTenant(tenant));

        // Login stats
        dashboard.setLoginStats(getLoginStatsForTenant(tenant.getTenantID()));

        // Security stats
        dashboard.setSecurityStats(getSecurityStatsForTenant(tenant.getTenantID()));

        // Policy stats
        dashboard.setPolicyStats(getPolicyStatsForTenant(tenant.getTenantID()));

        // No license stats for Enterprise
        dashboard.setLicenseStats(null);

        // Device & browser event stats
        LocalDateTime nowE = LocalDateTime.now();
        LocalDateTime startOfDayE = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeekE = startOfDayE.minusDays(7);
        LocalDateTime startOfMonthE = startOfDayE.minusDays(30);
        dashboard.setDeviceStats(getBrowserEventStats(tenant.getTenantID(), startOfDayE, startOfWeekE, startOfMonthE, nowE));

        return dashboard;
    }

    // ============================================================
    // SELF-MANAGED OWN-ORG STATS
    // ============================================================

    /**
     * Stats scoped only to the selfManaged tenant's own users/groups/policies.
     * Used to populate the "My Organization" panel in the mode-switched dashboard.
     */
    private MyOrgStatsDTO getMyOrgStats(String tenantId) {
        long ownUserCount = userRepository.countByTenant_TenantID(tenantId);
        long activeUserCount = userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        long groupCount = eventsGroupRepository.countByTenantIdAndIsActive(tenantId, true);
        long authorizedGroupCount = eventsGroupRepository
                .countByTenantIdAndIsActiveAndAuthorized(tenantId, true, true);
        long policyAssignmentCount = policyAssignmentRepository.countByTenantId(tenantId);
        long activeDeviceCount = browserDeviceRepository
                .countByTenantIdAndStatus(tenantId, BrowserDeviceStatus.ACTIVE);

        return MyOrgStatsDTO.builder()
                .ownUserCount(ownUserCount)
                .activeUserCount(activeUserCount)
                .groupCount(groupCount)
                .authorizedGroupCount(authorizedGroupCount)
                .policyAssignmentCount(policyAssignmentCount)
                .activeDeviceCount(activeDeviceCount)
                .build();
    }

    // ============================================================
    // TENANT STATS
    // ============================================================

    private TenantStatsDTO getTenantStatsForPlatformAdmin() {
        return getGlobalTenantStats();
    }

    /**
     * Shared global tenant stats used by both PLATFORM_ADMIN and MASTER_MSSP.
     */
    private TenantStatsDTO getGlobalTenantStats() {
        long totalMssps = tenantRepository.countAllMssps();
        long activeMssps = tenantRepository.countActiveMssps();
        long totalEnterprises = tenantRepository.countAllEnterprises();
        long activeEnterprises = tenantRepository.countActiveEnterprises();
        long pendingEnterprises = tenantRepository.countByTenantTypeAndStatus("ENTERPRISE", "PENDING");
        long suspendedTenants = tenantRepository.countByTenantTypeAndStatus("MSSP", "SUSPENDED")
                + tenantRepository.countByTenantTypeAndStatus("ENTERPRISE", "SUSPENDED");

        return TenantStatsDTO.forPlatformAdmin(totalMssps, activeMssps, totalEnterprises,
                activeEnterprises, pendingEnterprises, suspendedTenants);
    }

    /**
     * Tenant stats scoped to a specific MASTER_MSSP's tree.
     * Counts only MSSPs and Enterprises that belong under this MASTER_MSSP.
     */
    private TenantStatsDTO getTenantStatsForMasterMssp(String masterMsspId) {
        long totalMssps = tenantRepository.countMsspsByParentTenantId(masterMsspId);
        long activeMssps = tenantRepository.countActiveMsspsByParentTenantId(masterMsspId);
        long totalEnterprises = tenantRepository.countEnterprisesUnderMasterMssp(masterMsspId);
        long activeEnterprises = tenantRepository.countActiveEnterprisesUnderMasterMssp(masterMsspId);
        long pendingEnterprises = tenantRepository.countPendingEnterprisesUnderMasterMssp(masterMsspId);
        long suspendedTenants = tenantRepository.countSuspendedTenantsUnderMasterMssp(masterMsspId);

        return TenantStatsDTO.forMasterMssp(totalMssps, activeMssps, totalEnterprises,
                activeEnterprises, pendingEnterprises, suspendedTenants);
    }

    private TenantStatsDTO getTenantStatsForMssp(String msspTenantId) {
        long totalEnterprises = tenantRepository.countByParentTenantId(msspTenantId);
        long activeEnterprises = tenantRepository.countByParentTenantIdAndStatus(msspTenantId, "ACTIVE");
        long pendingEnterprises = tenantRepository.countByParentTenantIdAndStatus(msspTenantId, "PENDING");
        long suspendedTenants = tenantRepository.countByParentTenantIdAndStatus(msspTenantId, "SUSPENDED");

        return TenantStatsDTO.forMssp(totalEnterprises, activeEnterprises, pendingEnterprises, suspendedTenants);
    }

    // ============================================================
    // USER STATS
    // ============================================================

    private UserStatsDTO getUserStatsForPlatformAdmin() {
        try {
            // Use DB counts instead of N+1 Keycloak calls per tenant
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countByStatus("ACTIVE");
            long inactiveUsers = userRepository.countByStatus("INACTIVE");
            long lockedUsers = userRepository.countByStatus("LOCKED");
            long pendingVerification = userRepository.countByStatus("PENDING");

            return UserStatsDTO.forHierarchy(
                    totalUsers, activeUsers, inactiveUsers, lockedUsers,
                    pendingVerification, 0, 0, totalUsers - 0, totalUsers);

        } catch (Exception e) {
            log.warn("Failed to get user stats for platform admin: {}", e.getMessage());
            return UserStatsDTO.builder().build();
        }
    }

    private UserStatsDTO getUserStatsForTenant(Tenant tenant) {
        try {
            String tenantId = tenant.getTenantID();

            // Use DB counts for accurate active/inactive/locked breakdown
            long totalUsers = userRepository.countByTenant_TenantID(tenantId);
            long activeUsers = userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
            long inactiveUsers = userRepository.countByTenantIdAndStatus(tenantId, "INACTIVE");
            long lockedUsers = userRepository.countByTenantIdAndStatus(tenantId, "LOCKED");
            long pendingVerification = userRepository.countByTenantIdAndStatus(tenantId, "PENDING");

            return UserStatsDTO.forSingleTenant(
                    totalUsers, activeUsers, inactiveUsers, lockedUsers,
                    pendingVerification, 0, 0, totalUsers);

        } catch (Exception e) {
            log.warn("Failed to get user stats for tenant {}: {}", tenant.getTenantID(), e.getMessage());
            return UserStatsDTO.builder().build();
        }
    }

    /**
     * User stats for MSSP: own tenant users + total across all child enterprises.
     * usersAcrossAllTenants = MSSP users + all enterprise users whose parent is this MSSP.
     */
    private UserStatsDTO getUserStatsForMssp(Tenant mssp) {
        try {
            String msspId = mssp.getTenantID();

            long totalUsers = userRepository.countByTenant_TenantID(msspId);
            long activeUsers = userRepository.countByTenantIdAndStatus(msspId, "ACTIVE");
            long inactiveUsers = userRepository.countByTenantIdAndStatus(msspId, "INACTIVE");
            long lockedUsers = userRepository.countByTenantIdAndStatus(msspId, "LOCKED");
            long pendingVerification = userRepository.countByTenantIdAndStatus(msspId, "PENDING");

            // Users in all child enterprises
            long usersInChildEnterprises = userRepository.countByTenant_ParentTenantId(msspId);
            long usersAcrossAllTenants = totalUsers + usersInChildEnterprises;

            return UserStatsDTO.forHierarchy(
                    totalUsers, activeUsers, inactiveUsers, lockedUsers,
                    pendingVerification, 0, 0, totalUsers, usersAcrossAllTenants);

        } catch (Exception e) {
            log.warn("Failed to get user stats for MSSP {}: {}", mssp.getTenantID(), e.getMessage());
            return UserStatsDTO.builder().build();
        }
    }

    /**
     * User stats for MASTER_MSSP: own tenant users + users in child MSSPs + users in grandchild enterprises.
     * usersAcrossAllTenants covers the full 3-level tree.
     */
    private UserStatsDTO getUserStatsForMasterMssp(Tenant masterMssp) {
        try {
            String masterMsspId = masterMssp.getTenantID();

            long totalUsers = userRepository.countByTenant_TenantID(masterMsspId);
            long activeUsers = userRepository.countByTenantIdAndStatus(masterMsspId, "ACTIVE");
            long inactiveUsers = userRepository.countByTenantIdAndStatus(masterMsspId, "INACTIVE");
            long lockedUsers = userRepository.countByTenantIdAndStatus(masterMsspId, "LOCKED");
            long pendingVerification = userRepository.countByTenantIdAndStatus(masterMsspId, "PENDING");

            // Users in direct child MSSPs
            long usersInChildMssps = userRepository.countByTenant_ParentTenantId(masterMsspId);

            // Users in enterprises under child MSSPs (2-level deep)
            List<String> childMsspIds = tenantRepository.findMsspIdsByMasterMsspId(masterMsspId);
            long usersInEnterprisesUnderMssps = childMsspIds.isEmpty() ? 0
                    : userRepository.countByTenantParentTenantIdIn(childMsspIds);

            long usersAcrossAllTenants = totalUsers + usersInChildMssps + usersInEnterprisesUnderMssps;

            return UserStatsDTO.forHierarchy(
                    totalUsers, activeUsers, inactiveUsers, lockedUsers,
                    pendingVerification, 0, 0, totalUsers, usersAcrossAllTenants);

        } catch (Exception e) {
            log.warn("Failed to get user stats for MASTER_MSSP {}: {}", masterMssp.getTenantID(), e.getMessage());
            return UserStatsDTO.builder().build();
        }
    }

    // ============================================================
    // SESSION STATS
    // ============================================================

    private SessionStatsDTO getSessionStatsForPlatformAdmin(Tenant tenant) {
        // Use the platform admin's own realm session stats instead of N+1 Keycloak calls
        // across all tenants. Cross-tenant session aggregation would require async batch calls.
        return getSessionStatsForTenant(tenant);
    }

    private SessionStatsDTO getSessionStatsForTenant(Tenant tenant) {
        try {
            String realm = tenant.getRealmName();
            if (realm == null || realm.isEmpty()) {
                return SessionStatsDTO.builder().build();
            }

            int activeSessions = authService.getActiveSessionCount(tenant.getTenantID());
            Map<String, Long> sessionsByClient = authService.getSessionStats(tenant.getTenantID());

            return SessionStatsDTO.forSingleTenant(
                    activeSessions, 0, activeSessions, activeSessions,
                    0.0, sessionsByClient);

        } catch (Exception e) {
            log.warn("Failed to get session stats for tenant {}: {}", tenant.getTenantID(), e.getMessage());
            return SessionStatsDTO.builder().build();
        }
    }

    // ============================================================
    // LOGIN STATS
    // ============================================================

    /**
     * Optimized: uses consolidated query to reduce 9 count queries → 1.
     */
    private LoginStatsDTO getLoginStatsForPlatformAdmin() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime startOfWeek = startOfDay.minusDays(7);
            LocalDateTime startOfMonth = startOfDay.minusDays(30);

            // Consolidated login counts (9 queries → 1)
            List<Object[]> countResults = loginAuditRepository.getConsolidatedLoginCountsAllTenants(startOfDay, startOfWeek, startOfMonth, now);
            Object[] counts = countResults.isEmpty() ? new Object[9] : countResults.get(0);

            long loginsToday = toLong(counts[0]);
            long successfulToday = toLong(counts[1]);
            long failedToday = toLong(counts[2]);
            double successRate = loginsToday > 0 ? (double) successfulToday / loginsToday * 100 : 0;

            long loginsThisWeek = toLong(counts[3]);
            long successfulThisWeek = toLong(counts[4]);
            long failedThisWeek = toLong(counts[5]);

            long loginsThisMonth = toLong(counts[6]);
            long successfulThisMonth = toLong(counts[7]);
            long failedThisMonth = toLong(counts[8]);

            // Daily trends for all tenants
            List<LoginStatsDTO.DailyLoginTrend> dailyTrends = getDailyTrendsAllTenants(startOfWeek, now);

            // Logins by hour for all tenants
            Map<Integer, Long> loginsByHour = getLoginsByHourAllTenants(startOfDay, now);

            // Failure reasons for all tenants
            Map<String, Long> failureReasons = getFailureReasonsAllTenants(startOfWeek, now);

            return LoginStatsDTO.builder()
                    .loginsToday(loginsToday)
                    .successfulLoginsToday(successfulToday)
                    .failedLoginsToday(failedToday)
                    .successRateToday(successRate)
                    .loginsThisWeek(loginsThisWeek)
                    .successfulLoginsThisWeek(successfulThisWeek)
                    .failedLoginsThisWeek(failedThisWeek)
                    .loginsThisMonth(loginsThisMonth)
                    .successfulLoginsThisMonth(successfulThisMonth)
                    .failedLoginsThisMonth(failedThisMonth)
                    .dailyTrends(dailyTrends)
                    .loginsByHour(loginsByHour)
                    .failureReasons(failureReasons)
                    .loginsAcrossAllTenants(loginsThisMonth)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get login stats for platform admin: {}", e.getMessage());
            return LoginStatsDTO.empty();
        }
    }

    /**
     * Optimized: uses consolidated query to reduce 9 count queries → 1.
     */
    private LoginStatsDTO getLoginStatsForTenant(String tenantId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime startOfWeek = startOfDay.minusDays(7);
            LocalDateTime startOfMonth = startOfDay.minusDays(30);

            // Consolidated login counts (9 queries → 1)
            List<Object[]> countResults = loginAuditRepository.getConsolidatedLoginCounts(tenantId, startOfDay, startOfWeek, startOfMonth, now);
            Object[] counts = countResults.isEmpty() ? new Object[9] : countResults.get(0);

            long loginsToday = toLong(counts[0]);
            long successfulToday = toLong(counts[1]);
            long failedToday = toLong(counts[2]);
            double successRate = loginsToday > 0 ? (double) successfulToday / loginsToday * 100 : 0;

            long loginsThisWeek = toLong(counts[3]);
            long successfulThisWeek = toLong(counts[4]);
            long failedThisWeek = toLong(counts[5]);

            long loginsThisMonth = toLong(counts[6]);
            long successfulThisMonth = toLong(counts[7]);
            long failedThisMonth = toLong(counts[8]);

            // Daily trends
            List<LoginStatsDTO.DailyLoginTrend> dailyTrends = getDailyTrends(tenantId, startOfWeek, now);

            // Logins by hour
            Map<Integer, Long> loginsByHour = getLoginsByHour(tenantId, startOfDay, now);

            // Failure reasons
            Map<String, Long> failureReasons = getFailureReasons(tenantId, startOfWeek, now);

            return LoginStatsDTO.builder()
                    .loginsToday(loginsToday)
                    .successfulLoginsToday(successfulToday)
                    .failedLoginsToday(failedToday)
                    .successRateToday(successRate)
                    .loginsThisWeek(loginsThisWeek)
                    .successfulLoginsThisWeek(successfulThisWeek)
                    .failedLoginsThisWeek(failedThisWeek)
                    .loginsThisMonth(loginsThisMonth)
                    .successfulLoginsThisMonth(successfulThisMonth)
                    .failedLoginsThisMonth(failedThisMonth)
                    .dailyTrends(dailyTrends)
                    .loginsByHour(loginsByHour)
                    .failureReasons(failureReasons)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get login stats for tenant {}: {}", tenantId, e.getMessage());
            return LoginStatsDTO.empty();
        }
    }

    private List<LoginStatsDTO.DailyLoginTrend> getDailyTrends(String tenantId,
            LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getDailyLoginTrends(tenantId, start, end);
            return results.stream()
                    .map(row -> LoginStatsDTO.DailyLoginTrend.builder()
                            .date(row[0].toString())
                            .successfulLogins(((Number) row[1]).longValue())
                            .failedLogins(((Number) row[2]).longValue())
                            .totalLogins(((Number) row[1]).longValue() + ((Number) row[2]).longValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get daily trends: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<Integer, Long> getLoginsByHour(String tenantId, LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getLoginsByHour(tenantId, start, end);
            Map<Integer, Long> byHour = new HashMap<>();
            for (Object[] row : results) {
                int hour = ((Number) row[0]).intValue();
                long count = ((Number) row[1]).longValue();
                byHour.put(hour, count);
            }
            return byHour;
        } catch (Exception e) {
            log.warn("Failed to get logins by hour: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Long> getFailureReasons(String tenantId, LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getFailureReasons(tenantId, start, end);
            Map<String, Long> reasons = new HashMap<>();
            for (Object[] row : results) {
                String reason = row[0] != null ? row[0].toString() : "UNKNOWN";
                long count = ((Number) row[1]).longValue();
                reasons.put(reason, count);
            }
            return reasons;
        } catch (Exception e) {
            log.warn("Failed to get failure reasons: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // Platform Admin specific aggregation methods (across all tenants)
    private List<LoginStatsDTO.DailyLoginTrend> getDailyTrendsAllTenants(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getDailyLoginTrendsAllTenants(start, end);
            return results.stream()
                    .map(row -> LoginStatsDTO.DailyLoginTrend.builder()
                            .date(row[0].toString())
                            .successfulLogins(((Number) row[1]).longValue())
                            .failedLogins(((Number) row[2]).longValue())
                            .totalLogins(((Number) row[1]).longValue() + ((Number) row[2]).longValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get daily trends for all tenants: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<Integer, Long> getLoginsByHourAllTenants(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getLoginsByHourAllTenants(start, end);
            Map<Integer, Long> byHour = new HashMap<>();
            for (Object[] row : results) {
                int hour = ((Number) row[0]).intValue();
                long count = ((Number) row[1]).longValue();
                byHour.put(hour, count);
            }
            return byHour;
        } catch (Exception e) {
            log.warn("Failed to get logins by hour for all tenants: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Long> getFailureReasonsAllTenants(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getFailureReasonsAllTenants(start, end);
            Map<String, Long> reasons = new HashMap<>();
            for (Object[] row : results) {
                String reason = row[0] != null ? row[0].toString() : "UNKNOWN";
                long count = ((Number) row[1]).longValue();
                reasons.put(reason, count);
            }
            return reasons;
        } catch (Exception e) {
            log.warn("Failed to get failure reasons for all tenants: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ============================================================
    // SECURITY STATS
    // ============================================================

    private SecurityStatsDTO getSecurityStatsForPlatformAdmin() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime startOfWeek = startOfDay.minusDays(7);
            LocalDateTime startOfMonth = startOfDay.minusDays(30);

            // Brute force stats
            long accountLockedToday = loginAuditRepository.countAccountLockedEventsAllTenants(startOfDay, now);
            List<Object[]> suspiciousIps = loginAuditRepository.findSuspiciousIpsAllTenants(startOfWeek, 5);
            List<String> topBlockedIPs = suspiciousIps.stream()
                    .map(row -> row[0].toString())
                    .limit(10)
                    .collect(Collectors.toList());
            long bruteForceAttempts = suspiciousIps.stream()
                    .mapToLong(row -> ((Number) row[1]).longValue())
                    .sum();

            // Security events by type
            Map<String, Long> secEventsByType = toMap(
                    loginAuditRepository.getSecurityEventsByTypeAllTenants(startOfWeek, now));

            // Suspicious login attempts
            long suspiciousLogins = loginAuditRepository.countSecurityEventsAllTenants(startOfWeek, now);

            // Permanently locked users from DB
            long permanentlyLocked = userRepository.countByStatus("LOCKED");

            // MFA stats from login audit
            long totalUsers = userRepository.count();
            long mfaUsers = loginAuditRepository.countDistinctMfaUsersAllTenants(startOfMonth, now);
            double mfaRate = totalUsers > 0 ? (double) mfaUsers / totalUsers * 100 : 0;

            return SecurityStatsDTO.builder()
                    .blockedIPs(topBlockedIPs.size())
                    .temporarilyLockedUsers(accountLockedToday)
                    .permanentlyLockedUsers(permanentlyLocked)
                    .bruteForceAttemptsToday(bruteForceAttempts)
                    .mfaEnabledUsers(mfaUsers)
                    .mfaDisabledUsers(totalUsers - mfaUsers)
                    .mfaAdoptionRate(mfaRate)
                    .suspiciousLoginAttempts(suspiciousLogins)
                    .topBlockedIPs(topBlockedIPs)
                    .securityEventsByType(secEventsByType)
                    .securityEventsAcrossAllTenants(suspiciousLogins)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get security stats for platform admin: {}", e.getMessage());
            return SecurityStatsDTO.empty();
        }
    }

    private SecurityStatsDTO getSecurityStatsForTenant(String tenantId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime startOfWeek = startOfDay.minusDays(7);
            LocalDateTime startOfMonth = startOfDay.minusDays(30);

            // Brute force stats
            long accountLockedToday = loginAuditRepository.countAccountLockedEvents(tenantId, startOfDay, now);
            List<Object[]> suspiciousIps = loginAuditRepository.findSuspiciousIps(tenantId, startOfWeek, 5);
            List<String> topBlockedIPs = suspiciousIps.stream()
                    .map(row -> row[0].toString())
                    .limit(10)
                    .collect(Collectors.toList());
            long bruteForceAttempts = suspiciousIps.stream()
                    .mapToLong(row -> ((Number) row[1]).longValue())
                    .sum();

            // Security events by type
            Map<String, Long> secEventsByType = toMap(
                    loginAuditRepository.getSecurityEventsByType(tenantId, startOfWeek, now));

            // Suspicious login attempts
            long suspiciousLogins = loginAuditRepository.countSecurityEvents(tenantId, startOfWeek, now);

            // Permanently locked users from DB
            long permanentlyLocked = userRepository.countByTenantIdAndStatus(tenantId, "LOCKED");

            // MFA stats from login audit
            long totalUsers = userRepository.countByTenant_TenantID(tenantId);
            long mfaUsers = loginAuditRepository.countDistinctMfaUsers(tenantId, startOfMonth, now);
            double mfaRate = totalUsers > 0 ? (double) mfaUsers / totalUsers * 100 : 0;

            return SecurityStatsDTO.builder()
                    .blockedIPs(topBlockedIPs.size())
                    .temporarilyLockedUsers(accountLockedToday)
                    .permanentlyLockedUsers(permanentlyLocked)
                    .bruteForceAttemptsToday(bruteForceAttempts)
                    .mfaEnabledUsers(mfaUsers)
                    .mfaDisabledUsers(totalUsers - mfaUsers)
                    .mfaAdoptionRate(mfaRate)
                    .suspiciousLoginAttempts(suspiciousLogins)
                    .topBlockedIPs(topBlockedIPs)
                    .securityEventsByType(secEventsByType)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get security stats for tenant {}: {}", tenantId, e.getMessage());
            return SecurityStatsDTO.empty();
        }
    }

    // ============================================================
    // POLICY STATS
    // ============================================================

    private PolicyStatsDTO getPolicyStatsForPlatformAdmin() {
        try {
            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

            // All policy counts across all tenants
            long totalBrowser = browserPolicyRepository.count();
            long activeBrowser = browserPolicyRepository.countByIsActive(true);
            long totalExtension = extensionPolicyRepository.count();
            long activeExtension = extensionPolicyRepository.countByIsActive(true);
            long totalNetwork = networkPolicyRepository.count();
            long activeNetwork = networkPolicyRepository.countByIsActiveTrue();

            long totalPolicies = totalBrowser + totalExtension + totalNetwork;
            long activePolicies = activeBrowser + activeExtension + activeNetwork;

            // Groups
            long totalGroups = groupsRepository.count();
            long groupsWithPolicies = policyAssignmentRepository.countDistinctResourcesByType("GROUP");

            // Created/modified this week
            long createdThisWeek = browserPolicyRepository.countByCreatedAtAfter(weekAgo);
            long modifiedThisWeek = browserPolicyRepository.countByUpdatedAtAfterAndCreatedAtBefore(weekAgo, weekAgo);

            // Policy type distribution
            Map<String, Long> policiesByType = new HashMap<>();
            policiesByType.put("BROWSER", totalBrowser);
            policiesByType.put("EXTENSION", totalExtension);
            policiesByType.put("NETWORK", totalNetwork);

            return PolicyStatsDTO.builder()
                    .totalBrowserPolicies(totalPolicies)
                    .activeBrowserPolicies(activePolicies)
                    .inactiveBrowserPolicies(totalPolicies - activePolicies)
                    .totalUserGroups(totalGroups)
                    .userGroupsWithPolicies(groupsWithPolicies)
                    .userGroupsWithoutPolicies(totalGroups - groupsWithPolicies)
                    .policiesCreatedThisWeek(createdThisWeek)
                    .policiesModifiedThisWeek(modifiedThisWeek)
                    .policiesByType(policiesByType)
                    .policiesAcrossAllTenants(totalPolicies)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get policy stats for platform admin: {}", e.getMessage());
            return PolicyStatsDTO.empty();
        }
    }

    private PolicyStatsDTO getPolicyStatsForTenant(String tenantId) {
        try {
            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

            // Browser + Extension + Network policy counts
            long totalBrowser = browserPolicyRepository.countByFkTenantId(tenantId);
            long activeBrowser = browserPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true);
            long totalExtension = extensionPolicyRepository.countByFkTenantId(tenantId);
            long activeExtension = extensionPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true);
            long totalNetwork = networkPolicyRepository.countByFkTenantId(tenantId);
            long activeNetwork = networkPolicyRepository.countByFkTenantIdAndIsActiveTrue(tenantId);

            long totalPolicies = totalBrowser + totalExtension + totalNetwork;
            long activePolicies = activeBrowser + activeExtension + activeNetwork;

            // Groups
            long totalGroups = groupsRepository.countByTenantId(tenantId);
            long groupsWithPolicies = policyAssignmentRepository.countDistinctResourcesByTenantIdAndType(tenantId, "GROUP");

            // Created/modified this week
            long createdThisWeek = browserPolicyRepository.countByFkTenantIdAndCreatedAtAfter(tenantId, weekAgo);
            long modifiedThisWeek = browserPolicyRepository.countByFkTenantIdAndUpdatedAtAfterAndCreatedAtBefore(tenantId, weekAgo, weekAgo);

            // Policy type distribution
            Map<String, Long> policiesByType = new HashMap<>();
            policiesByType.put("BROWSER", totalBrowser);
            policiesByType.put("EXTENSION", totalExtension);
            policiesByType.put("NETWORK", totalNetwork);

            return PolicyStatsDTO.builder()
                    .totalBrowserPolicies(totalPolicies)
                    .activeBrowserPolicies(activePolicies)
                    .inactiveBrowserPolicies(totalPolicies - activePolicies)
                    .totalUserGroups(totalGroups)
                    .userGroupsWithPolicies(groupsWithPolicies)
                    .userGroupsWithoutPolicies(totalGroups - groupsWithPolicies)
                    .policiesCreatedThisWeek(createdThisWeek)
                    .policiesModifiedThisWeek(modifiedThisWeek)
                    .policiesByType(policiesByType)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get policy stats for tenant {}: {}", tenantId, e.getMessage());
            return PolicyStatsDTO.empty();
        }
    }

    // ============================================================
    // LICENSE STATS
    // ============================================================

    private LicenseStatsDTO getLicenseStatsForPlatformAdmin() {
        // TODO: Implement when license management is available - platform-wide license stats
        return LicenseStatsDTO.empty();
    }

    private LicenseStatsDTO getLicenseStatsForMasterMssp() {
        // TODO: Implement when license management is available
        return LicenseStatsDTO.empty();
    }

    private LicenseStatsDTO getLicenseStatsForMssp(Tenant tenant) {
        // TODO: Implement when license management is available
        return LicenseStatsDTO.empty();
    }

    // ============================================================
    // SPECIFIC STAT ENDPOINTS
    // ============================================================

    /**
     * Get tenant statistics only.
     */
    public TenantStatsDTO getTenantStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets full access
        if (isPlatformAdmin(tenant)) {
            return getTenantStatsForPlatformAdmin();
        }

        TenantTypeEnum tenantType = TenantTypeEnum.fromCode(tenant.getTenantType());

        switch (tenantType) {
            case MASTER_MSSP:
                return getTenantStatsForMasterMssp(tenantId);
            case MSSP:
                return getTenantStatsForMssp(tenantId);
            default:
                return null; // Enterprise doesn't have tenant stats
        }
    }

    /**
     * Get user statistics only.
     */
    public UserStatsDTO getUserStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets aggregated stats across all tenants
        if (isPlatformAdmin(tenant)) {
            return getUserStatsForPlatformAdmin();
        }

        TenantTypeEnum tenantType = TenantTypeEnum.fromCode(tenant.getTenantType());

        switch (tenantType) {
            case MASTER_MSSP:
                return getUserStatsForMasterMssp(tenant);
            case MSSP:
                return getUserStatsForMssp(tenant);
            default:
                return getUserStatsForTenant(tenant);
        }
    }

    /**
     * Get session statistics only.
     */
    public SessionStatsDTO getSessionStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets own realm session stats (avoids N+1 Keycloak calls)
        if (isPlatformAdmin(tenant)) {
            return getSessionStatsForPlatformAdmin(tenant);
        }

        return getSessionStatsForTenant(tenant);
    }

    /**
     * Get login statistics only.
     */
    public LoginStatsDTO getLoginStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets aggregated stats across all tenants
        if (isPlatformAdmin(tenant)) {
            return getLoginStatsForPlatformAdmin();
        }

        return getLoginStatsForTenant(tenantId);
    }

    /**
     * Get security statistics only.
     */
    public SecurityStatsDTO getSecurityStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets aggregated stats across all tenants
        if (isPlatformAdmin(tenant)) {
            return getSecurityStatsForPlatformAdmin();
        }

        return getSecurityStatsForTenant(tenantId);
    }

    /**
     * Get policy statistics only.
     */
    public PolicyStatsDTO getPolicyStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets aggregated stats across all tenants
        if (isPlatformAdmin(tenant)) {
            return getPolicyStatsForPlatformAdmin();
        }

        return getPolicyStatsForTenant(tenantId);
    }

    /**
     * Get license statistics only.
     */
    public LicenseStatsDTO getLicenseStats(String tenantId) {
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets full license stats
        if (isPlatformAdmin(tenant)) {
            return getLicenseStatsForPlatformAdmin();
        }

        TenantTypeEnum tenantType = TenantTypeEnum.fromCode(tenant.getTenantType());

        switch (tenantType) {
            case MASTER_MSSP:
                return getLicenseStatsForMasterMssp();
            case MSSP:
                return getLicenseStatsForMssp(tenant);
            default:
                return null; // Enterprise doesn't have license stats
        }
    }

    // ============================================================
    // TENANT DASHBOARD STATS (Active Devices, Events, Organization, Recent Users/Devices)
    // ============================================================

    /**
     * Get tenant dashboard statistics including active devices, events, organization stats,
     * recent users, and recent devices.
     * Cached for 5 minutes per tenantId to reduce DB load.
     */
    @Cacheable(value = "tenantDashboardStats", key = "#tenantId")
    public TenantDashboardStatsDTO getTenantDashboardStats(String tenantId) {
        log.info("Getting tenant dashboard stats for tenantId={}", tenantId);
        Tenant tenant = getTenant(tenantId);

        // Platform Admin gets aggregated stats across all tenants
        if (isPlatformAdmin(tenant)) {
            log.info("Tenant {} is PLATFORM_ADMIN, providing aggregated stats across all tenants", tenantId);
            return getTenantDashboardStatsForPlatformAdmin();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeek = startOfDay.minusDays(7);
        LocalDateTime startOfMonth = startOfDay.minusDays(30);

        try {
            // Active Devices
            long activeDevices = loginAuditRepository.countActiveDevices(tenantId, startOfDay, now);
            Map<String, Long> devicesByType = getDevicesByType(tenantId, startOfDay, now);

            // Total Events
            long totalEventsToday = loginAuditRepository.countTotalEvents(tenantId, startOfDay, now);
            long totalEventsThisWeek = loginAuditRepository.countTotalEvents(tenantId, startOfWeek, now);
            long totalEventsThisMonth = loginAuditRepository.countTotalEvents(tenantId, startOfMonth, now);

            // Security Events
            long securityEventsToday = loginAuditRepository.countSecurityEvents(tenantId, startOfDay, now);
            long securityEventsThisWeek = loginAuditRepository.countSecurityEvents(tenantId, startOfWeek, now);
            long securityEventsThisMonth = loginAuditRepository.countSecurityEvents(tenantId, startOfMonth, now);
            Map<String, Long> securityEventsByType = getSecurityEventsByType(tenantId, startOfWeek, now);

            // Organization Stats
            TenantDashboardStatsDTO.OrganizationStatsDTO orgStats = getOrganizationStats(tenantId);

            // Recent Users
            List<TenantDashboardStatsDTO.RecentUserDTO> recentUsers = getRecentUsers(tenantId, 10);

            // Recent Devices
            List<TenantDashboardStatsDTO.RecentDeviceDTO> recentDevices = getRecentDevices(tenantId, startOfDay, 10);

            // Browser Event Stats (from sfn-events-api shared database)
            BrowserEventStatsDTO browserEventStats = getBrowserEventStats(tenantId, startOfDay, startOfWeek, startOfMonth, now);

            return TenantDashboardStatsDTO.builder()
                    .activeDevices(activeDevices)
                    .devicesByType(devicesByType)
                    .totalEventsToday(totalEventsToday)
                    .totalEventsThisWeek(totalEventsThisWeek)
                    .totalEventsThisMonth(totalEventsThisMonth)
                    .securityEventsToday(securityEventsToday)
                    .securityEventsThisWeek(securityEventsThisWeek)
                    .securityEventsThisMonth(securityEventsThisMonth)
                    .securityEventsByType(securityEventsByType)
                    .organization(orgStats)
                    .recentUsers(recentUsers)
                    .recentDevices(recentDevices)
                    .browserEventStats(browserEventStats)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get tenant dashboard stats for {}: {}", tenantId, e.getMessage(), e);
            return TenantDashboardStatsDTO.empty();
        }
    }

    private Map<String, Long> getDevicesByType(String tenantId, LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.countDevicesByType(tenantId, start, end);
            Map<String, Long> devicesByType = new HashMap<>();
            for (Object[] row : results) {
                String deviceType = row[0] != null ? row[0].toString() : "Unknown";
                long count = ((Number) row[1]).longValue();
                devicesByType.put(deviceType, count);
            }
            return devicesByType;
        } catch (Exception e) {
            log.warn("Failed to get devices by type for tenant {}: {}", tenantId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Long> getSecurityEventsByType(String tenantId, LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getSecurityEventsByType(tenantId, start, end);
            Map<String, Long> eventsByType = new HashMap<>();
            for (Object[] row : results) {
                String eventType = row[0] != null ? row[0].toString() : "UNKNOWN";
                long count = ((Number) row[1]).longValue();
                eventsByType.put(eventType, count);
            }
            return eventsByType;
        } catch (Exception e) {
            log.warn("Failed to get security events by type for tenant {}: {}", tenantId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private TenantDashboardStatsDTO.OrganizationStatsDTO getOrganizationStats(String tenantId) {
        try {
            long totalUsers = userRepository.countByTenant_TenantID(tenantId);
            long activeUsers = userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
            long inactiveUsers = userRepository.countByTenantIdAndStatus(tenantId, "INACTIVE");
            long lockedUsers = userRepository.countByTenantIdAndStatus(tenantId, "LOCKED");
            long totalGroups = groupsRepository.countByTenantId(tenantId);
            long totalRoles = rolesRepository.countByTenant_TenantID(tenantId);

            return TenantDashboardStatsDTO.OrganizationStatsDTO.builder()
                    .totalUsers(totalUsers)
                    .activeUsers(activeUsers)
                    .inactiveUsers(inactiveUsers)
                    .lockedUsers(lockedUsers)
                    .totalGroups(totalGroups)
                    .totalRoles(totalRoles)
                    .adminUsers(0) // TODO: Count admin users when admin flag is available
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get organization stats for tenant {}: {}", tenantId, e.getMessage());
            return TenantDashboardStatsDTO.OrganizationStatsDTO.builder().build();
        }
    }

    private List<TenantDashboardStatsDTO.RecentUserDTO> getRecentUsers(String tenantId, int limit) {
        try {
            List<Object[]> results = loginAuditRepository.findRecentUserLogins(tenantId, limit);
            return results.stream()
                    .map(row -> {
                        String userAgent = row[6] != null ? row[6].toString() : "";
                        return TenantDashboardStatsDTO.RecentUserDTO.builder()
                                .userId(row[0] != null ? row[0].toString() : null)
                                .username(row[1] != null ? row[1].toString() : null)
                                .email(row[2] != null ? row[2].toString() : null)
                                .lastLoginTime(row[3] != null ? ((java.sql.Timestamp) row[3]).toLocalDateTime() : null)
                                .ipAddress(row[4] != null ? row[4].toString() : null)
                                .location(row[5] != null ? row[5].toString() : null)
                                .deviceType(parseDeviceType(userAgent))
                                .loginSuccess(row[7] != null && (Boolean) row[7])
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get recent users for tenant {}: {}", tenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TenantDashboardStatsDTO.RecentDeviceDTO> getRecentDevices(String tenantId, LocalDateTime since, int limit) {
        try {
            List<Object[]> results = loginAuditRepository.findRecentDevices(tenantId, since, limit);
            return results.stream()
                    .map(row -> {
                        String userAgent = row[1] != null ? row[1].toString() : "";
                        return TenantDashboardStatsDTO.RecentDeviceDTO.builder()
                                .deviceInfo(row[0] != null ? row[0].toString() : null)
                                .userAgent(userAgent)
                                .deviceType(parseDeviceType(userAgent))
                                .ipAddress(row[2] != null ? row[2].toString() : null)
                                .location(row[3] != null ? row[3].toString() : null)
                                .lastSeenAt(row[4] != null ? ((java.sql.Timestamp) row[4]).toLocalDateTime() : null)
                                .lastUsedBy(row[5] != null ? row[5].toString() : null)
                                .sessionCount(row[6] != null ? ((Number) row[6]).longValue() : 0)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get recent devices for tenant {}: {}", tenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "Tablet";
        } else if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) {
            return "Desktop";
        }
        return "Unknown";
    }

    // ============================================================
    // PLATFORM ADMIN - TENANT DASHBOARD STATS (Aggregated across ALL tenants)
    // ============================================================

    /**
     * Get tenant dashboard statistics for Platform Admin - aggregated across ALL tenants.
     */
    private TenantDashboardStatsDTO getTenantDashboardStatsForPlatformAdmin() {
        log.info("Getting tenant dashboard stats for PLATFORM_ADMIN (aggregated across all tenants)");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime startOfWeek = startOfDay.minusDays(7);
        LocalDateTime startOfMonth = startOfDay.minusDays(30);

        try {
            // Active Devices (across all tenants)
            long activeDevices = loginAuditRepository.countActiveDevicesAllTenants(startOfDay, now);
            Map<String, Long> devicesByType = getDevicesByTypeAllTenants(startOfDay, now);

            // Total Events (across all tenants)
            long totalEventsToday = loginAuditRepository.countTotalEventsAllTenants(startOfDay, now);
            long totalEventsThisWeek = loginAuditRepository.countTotalEventsAllTenants(startOfWeek, now);
            long totalEventsThisMonth = loginAuditRepository.countTotalEventsAllTenants(startOfMonth, now);

            // Security Events (across all tenants)
            long securityEventsToday = loginAuditRepository.countSecurityEventsAllTenants(startOfDay, now);
            long securityEventsThisWeek = loginAuditRepository.countSecurityEventsAllTenants(startOfWeek, now);
            long securityEventsThisMonth = loginAuditRepository.countSecurityEventsAllTenants(startOfMonth, now);
            Map<String, Long> securityEventsByType = getSecurityEventsByTypeAllTenants(startOfWeek, now);

            // Organization Stats (aggregated across all tenants)
            TenantDashboardStatsDTO.OrganizationStatsDTO orgStats = getOrganizationStatsAllTenants();

            // Recent Users (across all tenants)
            List<TenantDashboardStatsDTO.RecentUserDTO> recentUsers = getRecentUsersAllTenants(10);

            // Recent Devices (across all tenants)
            List<TenantDashboardStatsDTO.RecentDeviceDTO> recentDevices = getRecentDevicesAllTenants(startOfDay, 10);

            // Browser Event Stats (across all tenants)
            BrowserEventStatsDTO browserEventStats = getBrowserEventStatsForPlatformAdmin(startOfDay, startOfWeek, startOfMonth, now);

            return TenantDashboardStatsDTO.builder()
                    .activeDevices(activeDevices)
                    .devicesByType(devicesByType)
                    .totalEventsToday(totalEventsToday)
                    .totalEventsThisWeek(totalEventsThisWeek)
                    .totalEventsThisMonth(totalEventsThisMonth)
                    .securityEventsToday(securityEventsToday)
                    .securityEventsThisWeek(securityEventsThisWeek)
                    .securityEventsThisMonth(securityEventsThisMonth)
                    .securityEventsByType(securityEventsByType)
                    .organization(orgStats)
                    .recentUsers(recentUsers)
                    .recentDevices(recentDevices)
                    .browserEventStats(browserEventStats)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get tenant dashboard stats for Platform Admin: {}", e.getMessage(), e);
            return TenantDashboardStatsDTO.empty();
        }
    }

    private Map<String, Long> getDevicesByTypeAllTenants(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.countDevicesByTypeAllTenants(start, end);
            Map<String, Long> devicesByType = new HashMap<>();
            for (Object[] row : results) {
                String deviceType = row[0] != null ? row[0].toString() : "Unknown";
                long count = ((Number) row[1]).longValue();
                devicesByType.put(deviceType, count);
            }
            return devicesByType;
        } catch (Exception e) {
            log.warn("Failed to get devices by type for all tenants: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Long> getSecurityEventsByTypeAllTenants(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = loginAuditRepository.getSecurityEventsByTypeAllTenants(start, end);
            Map<String, Long> eventsByType = new HashMap<>();
            for (Object[] row : results) {
                String eventType = row[0] != null ? row[0].toString() : "UNKNOWN";
                long count = ((Number) row[1]).longValue();
                eventsByType.put(eventType, count);
            }
            return eventsByType;
        } catch (Exception e) {
            log.warn("Failed to get security events by type for all tenants: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private TenantDashboardStatsDTO.OrganizationStatsDTO getOrganizationStatsAllTenants() {
        try {
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countByStatus("ACTIVE");
            long inactiveUsers = userRepository.countByStatus("INACTIVE");
            long lockedUsers = userRepository.countByStatus("LOCKED");
            long totalGroups = groupsRepository.count();
            long totalRoles = rolesRepository.count();
            long totalTenants = tenantRepository.count();

            return TenantDashboardStatsDTO.OrganizationStatsDTO.builder()
                    .totalUsers(totalUsers)
                    .activeUsers(activeUsers)
                    .inactiveUsers(inactiveUsers)
                    .lockedUsers(lockedUsers)
                    .totalGroups(totalGroups)
                    .totalRoles(totalRoles)
                    .adminUsers(0) // TODO: Count admin users when admin flag is available
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get organization stats for all tenants: {}", e.getMessage());
            return TenantDashboardStatsDTO.OrganizationStatsDTO.builder().build();
        }
    }

    private List<TenantDashboardStatsDTO.RecentUserDTO> getRecentUsersAllTenants(int limit) {
        try {
            List<Object[]> results = loginAuditRepository.findRecentUserLoginsAllTenants(limit);
            return results.stream()
                    .map(row -> {
                        String userAgent = row[6] != null ? row[6].toString() : "";
                        return TenantDashboardStatsDTO.RecentUserDTO.builder()
                                .userId(row[0] != null ? row[0].toString() : null)
                                .username(row[1] != null ? row[1].toString() : null)
                                .email(row[2] != null ? row[2].toString() : null)
                                .lastLoginTime(row[3] != null ? ((java.sql.Timestamp) row[3]).toLocalDateTime() : null)
                                .ipAddress(row[4] != null ? row[4].toString() : null)
                                .location(row[5] != null ? row[5].toString() : null)
                                .deviceType(parseDeviceType(userAgent))
                                .loginSuccess(row[7] != null && (Boolean) row[7])
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get recent users for all tenants: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TenantDashboardStatsDTO.RecentDeviceDTO> getRecentDevicesAllTenants(LocalDateTime since, int limit) {
        try {
            List<Object[]> results = loginAuditRepository.findRecentDevicesAllTenants(since, limit);
            return results.stream()
                    .map(row -> {
                        String userAgent = row[1] != null ? row[1].toString() : "";
                        return TenantDashboardStatsDTO.RecentDeviceDTO.builder()
                                .deviceInfo(row[0] != null ? row[0].toString() : null)
                                .userAgent(userAgent)
                                .deviceType(parseDeviceType(userAgent))
                                .ipAddress(row[2] != null ? row[2].toString() : null)
                                .location(row[3] != null ? row[3].toString() : null)
                                .lastSeenAt(row[4] != null ? ((java.sql.Timestamp) row[4]).toLocalDateTime() : null)
                                .lastUsedBy(row[5] != null ? row[5].toString() : null)
                                .sessionCount(row[6] != null ? ((Number) row[6]).longValue() : 0)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get recent devices for all tenants: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================================================
    // BROWSER EVENT STATS (from sfn-events-api shared database)
    // ============================================================

    /**
     * Get browser event statistics for a specific tenant.
     * Optimized: uses consolidated queries to reduce 15 count queries → 1, and 4 device counts → 1.
     */
    private BrowserEventStatsDTO getBrowserEventStats(String tenantId,
                                                       LocalDateTime startOfDay,
                                                       LocalDateTime startOfWeek,
                                                       LocalDateTime startOfMonth,
                                                       LocalDateTime now) {
        try {
            log.debug("Fetching browser event stats for tenant={}", tenantId);

            // Consolidated event counts (15 queries → 1)
            List<Object[]> countResults = browserEventRepository.getConsolidatedCounts(tenantId, startOfDay, startOfWeek, startOfMonth, now);
            Object[] counts = countResults.isEmpty() ? new Object[15] : countResults.get(0);

            long totalEventsToday = toLong(counts[0]);
            long totalEventsThisWeek = toLong(counts[1]);
            long totalEventsThisMonth = toLong(counts[2]);
            long policyViolationsToday = toLong(counts[3]);
            long policyViolationsThisWeek = toLong(counts[4]);
            long policyViolationsThisMonth = toLong(counts[5]);
            long blockedEventsToday = toLong(counts[6]);
            long blockedEventsThisWeek = toLong(counts[7]);
            long blockedEventsThisMonth = toLong(counts[8]);
            long browserSecurityEventsToday = toLong(counts[9]);
            long browserSecurityEventsThisWeek = toLong(counts[10]);
            long browserSecurityEventsThisMonth = toLong(counts[11]);
            long activeUsersToday = toLong(counts[12]);
            long activeUsersThisWeek = toLong(counts[13]);
            long activeUsersThisMonth = toLong(counts[14]);

            // Breakdowns (these are GROUP BY queries - can't easily consolidate further)
            Map<String, Long> eventsByType = toMap(browserEventRepository.countByEventType(tenantId, startOfWeek, now));
            Map<String, Long> eventsByCategory = toMap(browserEventRepository.countByCategory(tenantId, startOfWeek, now));
            Map<String, Long> fileOperationsByType = toMap(browserEventRepository.countByFileOperationType(tenantId, startOfWeek, now));
            Map<String, Long> securityEventsByThreatType = toMap(browserEventRepository.countByThreatType(tenantId, startOfWeek, now));
            Map<String, Long> eventsBySeverity = toMap(browserEventRepository.countBySeverity(tenantId, startOfWeek, now));

            // Top domains
            List<BrowserEventStatsDTO.DomainStats> topDomains = browserEventRepository.getTopDomains(tenantId, startOfWeek, now)
                    .stream()
                    .limit(10)
                    .map(row -> BrowserEventStatsDTO.DomainStats.builder()
                            .domain(row[0] != null ? row[0].toString() : "Unknown")
                            .eventCount(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());

            // Top users
            List<BrowserEventStatsDTO.UserActivityStats> topActiveUsers = browserEventRepository.getTopUsers(tenantId, startOfWeek, now)
                    .stream()
                    .limit(10)
                    .map(row -> BrowserEventStatsDTO.UserActivityStats.builder()
                            .userName(row[0] != null ? row[0].toString() : "Unknown")
                            .eventCount(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());

            // Daily trends
            List<BrowserEventStatsDTO.DailyEventTrend> dailyTrends = browserEventRepository.getDailyTrends(tenantId, startOfWeek, now)
                    .stream()
                    .map(row -> BrowserEventStatsDTO.DailyEventTrend.builder()
                            .date(row[0] != null ? row[0].toString() : "")
                            .totalEvents(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());

            // Consolidated device counts (4 queries → 1)
            List<Object[]> deviceCountResults = browserDeviceRepository.getConsolidatedDeviceCounts(tenantId);
            Object[] deviceCounts = deviceCountResults.isEmpty() ? new Object[4] : deviceCountResults.get(0);
            long totalRegisteredDevices = toLong(deviceCounts[0]);
            long activeDevices = toLong(deviceCounts[1]);
            long inactiveDevices = toLong(deviceCounts[2]);
            long blockedDevices = toLong(deviceCounts[3]);

            Map<String, Long> devicesByType = toMap(browserDeviceRepository.countByDeviceType(tenantId));
            Map<String, Long> devicesByBrowser = toMap(browserDeviceRepository.countByBrowserType(tenantId));
            Map<String, Long> devicesByOs = toMap(browserDeviceRepository.countByOsInfo(tenantId));
            Map<String, Long> devicesByExtensionVersion = toMap(browserDeviceRepository.countByExtensionVersion(tenantId));

            // Recent devices
            List<BrowserEventStatsDTO.RecentDeviceInfo> recentBrowserDevices = browserDeviceRepository.findRecentDevices(tenantId)
                    .stream()
                    .limit(10)
                    .map(this::toRecentDeviceInfo)
                    .collect(Collectors.toList());

            return BrowserEventStatsDTO.builder()
                    .totalEventsToday(totalEventsToday)
                    .totalEventsThisWeek(totalEventsThisWeek)
                    .totalEventsThisMonth(totalEventsThisMonth)
                    .policyViolationsToday(policyViolationsToday)
                    .policyViolationsThisWeek(policyViolationsThisWeek)
                    .policyViolationsThisMonth(policyViolationsThisMonth)
                    .blockedEventsToday(blockedEventsToday)
                    .blockedEventsThisWeek(blockedEventsThisWeek)
                    .blockedEventsThisMonth(blockedEventsThisMonth)
                    .browserSecurityEventsToday(browserSecurityEventsToday)
                    .browserSecurityEventsThisWeek(browserSecurityEventsThisWeek)
                    .browserSecurityEventsThisMonth(browserSecurityEventsThisMonth)
                    .activeUsersToday(activeUsersToday)
                    .activeUsersThisWeek(activeUsersThisWeek)
                    .activeUsersThisMonth(activeUsersThisMonth)
                    .eventsByType(eventsByType)
                    .eventsByCategory(eventsByCategory)
                    .fileOperationsByType(fileOperationsByType)
                    .securityEventsByThreatType(securityEventsByThreatType)
                    .eventsBySeverity(eventsBySeverity)
                    .topDomains(topDomains)
                    .topActiveUsers(topActiveUsers)
                    .dailyTrends(dailyTrends)
                    .totalRegisteredDevices(totalRegisteredDevices)
                    .activeDevices(activeDevices)
                    .inactiveDevices(inactiveDevices)
                    .blockedDevices(blockedDevices)
                    .devicesByType(devicesByType)
                    .devicesByBrowser(devicesByBrowser)
                    .devicesByOs(devicesByOs)
                    .devicesByExtensionVersion(devicesByExtensionVersion)
                    .recentDevices(recentBrowserDevices)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get browser event stats for tenant {}: {}", tenantId, e.getMessage());
            return BrowserEventStatsDTO.empty();
        }
    }

    /**
     * Get browser event statistics for Platform Admin - aggregated across all tenants.
     * Optimized: uses consolidated queries to reduce 15 count queries → 1, and 4 device counts → 1.
     */
    private BrowserEventStatsDTO getBrowserEventStatsForPlatformAdmin(LocalDateTime startOfDay,
                                                                       LocalDateTime startOfWeek,
                                                                       LocalDateTime startOfMonth,
                                                                       LocalDateTime now) {
        try {
            log.debug("Fetching browser event stats for Platform Admin (all tenants)");

            // Consolidated event counts (15 queries → 1)
            List<Object[]> countResults = browserEventRepository.getConsolidatedCountsAllTenants(startOfDay, startOfWeek, startOfMonth, now);
            Object[] counts = countResults.isEmpty() ? new Object[15] : countResults.get(0);

            long totalEventsToday = toLong(counts[0]);
            long totalEventsThisWeek = toLong(counts[1]);
            long totalEventsThisMonth = toLong(counts[2]);
            long policyViolationsToday = toLong(counts[3]);
            long policyViolationsThisWeek = toLong(counts[4]);
            long policyViolationsThisMonth = toLong(counts[5]);
            long blockedEventsToday = toLong(counts[6]);
            long blockedEventsThisWeek = toLong(counts[7]);
            long blockedEventsThisMonth = toLong(counts[8]);
            long browserSecurityEventsToday = toLong(counts[9]);
            long browserSecurityEventsThisWeek = toLong(counts[10]);
            long browserSecurityEventsThisMonth = toLong(counts[11]);
            long activeUsersToday = toLong(counts[12]);
            long activeUsersThisWeek = toLong(counts[13]);
            long activeUsersThisMonth = toLong(counts[14]);

            // Breakdowns
            Map<String, Long> eventsByType = toMap(browserEventRepository.countByEventTypeAllTenants(startOfWeek, now));
            Map<String, Long> eventsByCategory = toMap(browserEventRepository.countByCategoryAllTenants(startOfWeek, now));
            Map<String, Long> fileOperationsByType = toMap(browserEventRepository.countByFileOperationTypeAllTenants(startOfWeek, now));
            Map<String, Long> securityEventsByThreatType = toMap(browserEventRepository.countByThreatTypeAllTenants(startOfWeek, now));
            Map<String, Long> eventsBySeverity = toMap(browserEventRepository.countBySeverityAllTenants(startOfWeek, now));

            // Top domains
            List<BrowserEventStatsDTO.DomainStats> topDomains = browserEventRepository.getTopDomainsAllTenants(startOfWeek, now)
                    .stream()
                    .limit(10)
                    .map(row -> BrowserEventStatsDTO.DomainStats.builder()
                            .domain(row[0] != null ? row[0].toString() : "Unknown")
                            .eventCount(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());

            // Daily trends
            List<BrowserEventStatsDTO.DailyEventTrend> dailyTrends = browserEventRepository.getDailyTrendsAllTenants(startOfWeek, now)
                    .stream()
                    .map(row -> BrowserEventStatsDTO.DailyEventTrend.builder()
                            .date(row[0] != null ? row[0].toString() : "")
                            .totalEvents(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());

            // Consolidated device counts (4+ queries → 1)
            List<Object[]> deviceCountResults = browserDeviceRepository.getConsolidatedDeviceCountsAllTenants();
            Object[] deviceCounts = deviceCountResults.isEmpty() ? new Object[4] : deviceCountResults.get(0);
            long totalRegisteredDevices = toLong(deviceCounts[0]);
            long activeDevices = toLong(deviceCounts[1]);
            long inactiveDevices = toLong(deviceCounts[2]);
            long blockedDevices = toLong(deviceCounts[3]);

            Map<String, Long> devicesByType = toMap(browserDeviceRepository.countByDeviceTypeAllTenants());
            Map<String, Long> devicesByBrowser = toMap(browserDeviceRepository.countByBrowserTypeAllTenants());
            Map<String, Long> devicesByOs = toMap(browserDeviceRepository.countByOsInfoAllTenants());
            Map<String, Long> devicesByExtensionVersion = toMap(browserDeviceRepository.countByExtensionVersionAllTenants());

            // Recent devices
            List<BrowserEventStatsDTO.RecentDeviceInfo> recentBrowserDevices = browserDeviceRepository.findRecentDevicesAllTenants()
                    .stream()
                    .limit(10)
                    .map(this::toRecentDeviceInfo)
                    .collect(Collectors.toList());

            return BrowserEventStatsDTO.builder()
                    .totalEventsToday(totalEventsToday)
                    .totalEventsThisWeek(totalEventsThisWeek)
                    .totalEventsThisMonth(totalEventsThisMonth)
                    .policyViolationsToday(policyViolationsToday)
                    .policyViolationsThisWeek(policyViolationsThisWeek)
                    .policyViolationsThisMonth(policyViolationsThisMonth)
                    .blockedEventsToday(blockedEventsToday)
                    .blockedEventsThisWeek(blockedEventsThisWeek)
                    .blockedEventsThisMonth(blockedEventsThisMonth)
                    .browserSecurityEventsToday(browserSecurityEventsToday)
                    .browserSecurityEventsThisWeek(browserSecurityEventsThisWeek)
                    .browserSecurityEventsThisMonth(browserSecurityEventsThisMonth)
                    .activeUsersToday(activeUsersToday)
                    .activeUsersThisWeek(activeUsersThisWeek)
                    .activeUsersThisMonth(activeUsersThisMonth)
                    .eventsByType(eventsByType)
                    .eventsByCategory(eventsByCategory)
                    .fileOperationsByType(fileOperationsByType)
                    .securityEventsByThreatType(securityEventsByThreatType)
                    .eventsBySeverity(eventsBySeverity)
                    .topDomains(topDomains)
                    .dailyTrends(dailyTrends)
                    .totalRegisteredDevices(totalRegisteredDevices)
                    .activeDevices(activeDevices)
                    .inactiveDevices(inactiveDevices)
                    .blockedDevices(blockedDevices)
                    .devicesByType(devicesByType)
                    .devicesByBrowser(devicesByBrowser)
                    .devicesByOs(devicesByOs)
                    .devicesByExtensionVersion(devicesByExtensionVersion)
                    .recentDevices(recentBrowserDevices)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get browser event stats for Platform Admin: {}", e.getMessage());
            return BrowserEventStatsDTO.empty();
        }
    }

    /**
     * Safely convert a possibly-null Object to long.
     */
    private long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : 0L;
    }

    /**
     * Convert list of Object[] to Map<String, Long>.
     */
    private Map<String, Long> toMap(List<Object[]> results) {
        Map<String, Long> map = new HashMap<>();
        if (results == null) {
            return map;
        }
        for (Object[] row : results) {
            String key = row[0] != null ? row[0].toString() : "Unknown";
            long value = ((Number) row[1]).longValue();
            map.put(key, value);
        }
        return map;
    }

    /**
     * Convert BrowserDevice to RecentDeviceInfo DTO.
     */
    private BrowserEventStatsDTO.RecentDeviceInfo toRecentDeviceInfo(BrowserDevice device) {
        return BrowserEventStatsDTO.RecentDeviceInfo.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .userName(device.getUserName())
                .deviceType(device.getDeviceType())
                .browserType(device.getBrowserType())
                .osInfo(device.getOsInfo())
                .extensionVersion(device.getExtensionVersion())
                .status(device.getStatus() != null ? device.getStatus().name() : null)
                .lastSeenAt(device.getLastSeenAt())
                .ipAddress(device.getIpAddress())
                .location(device.getLocation())
                .build();
    }

    // ============================================================
    // DATE RANGE STATS
    // ============================================================

    /**
     * Return dashboard statistics for an arbitrary caller-defined date range.
     *
     * Covers: logins, security events, audit events, browser extension events.
     * Role-based scoping mirrors all other dashboard methods:
     *   PLATFORM_ADMIN  → all tenants aggregated
     *   everyone else   → scoped to the requesting tenant
     *
     * @param tenantId  the tenant whose dashboard is being queried
     * @param startDate inclusive start of the range (ISO date, e.g. 2026-01-01)
     * @param endDate   inclusive end of the range (ISO date, e.g. 2026-01-31)
     * @throws GlobalException (BAD_REQUEST) if startDate > endDate or range > 366 days
     */
    public DateRangeStatsDTO getDateRangeStats(String tenantId,
                                                java.time.LocalDate startDate,
                                                java.time.LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new GlobalException("startDate must be before or equal to endDate",
                    ResponseCodes.BAD_REQUEST);
        }
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (totalDays > 366) {
            throw new GlobalException("Date range cannot exceed 366 days",
                    ResponseCodes.BAD_REQUEST);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        Tenant tenant = getTenant(tenantId);
        boolean isAdmin = isPlatformAdmin(tenant);

        try {
            // ── Login stats ────────────────────────────────────────────────
            long totalLogins;
            long successfulLogins;
            long failedLogins;
            List<LoginStatsDTO.DailyLoginTrend> dailyTrends;
            Map<Integer, Long> loginsByHour;
            Map<String, Long> failureReasons;

            // ── Security / audit ───────────────────────────────────────────
            long securityEvents;
            Map<String, Long> securityEventsByType;
            long accountLockedEvents;
            long mfaUsers;
            long totalAuditEvents;
            long activeDevices;
            Map<String, Long> devicesByType;
            Map<String, Long> auditEventsBySourceService;

            // ── Browser extension events ───────────────────────────────────
            long browserEvents;
            long policyViolations;
            long blockedBrowserEvents;
            long browserSecurityEvents;

            if (isAdmin) {
                totalLogins = loginAuditRepository.countTotalLoginsAllTenants(start, end);
                successfulLogins = loginAuditRepository.countSuccessfulLoginsAllTenants(start, end);
                failedLogins = loginAuditRepository.countFailedLoginsAllTenants(start, end);
                dailyTrends = buildDailyTrendsAllTenants(start, end);
                loginsByHour = toIntMap(loginAuditRepository.getLoginsByHourAllTenants(start, end));
                failureReasons = toMap(loginAuditRepository.getFailureReasonsAllTenants(start, end));

                securityEvents = loginAuditRepository.countSecurityEventsAllTenants(start, end);
                securityEventsByType = toMap(loginAuditRepository.getSecurityEventsByTypeAllTenants(start, end));
                accountLockedEvents = loginAuditRepository.countAccountLockedEventsAllTenants(start, end);
                mfaUsers = loginAuditRepository.countDistinctMfaUsersAllTenants(start, end);

                totalAuditEvents = loginAuditRepository.countTotalEventsAllTenants(start, end);
                activeDevices = loginAuditRepository.countActiveDevicesAllTenants(start, end);
                devicesByType = toMap(loginAuditRepository.countDevicesByTypeAllTenants(start, end));
                auditEventsBySourceService = Collections.emptyMap(); // requires tenant context

                browserEvents = browserEventRepository.countAllByTimeRange(start, end);
                policyViolations = browserEventRepository.countPolicyViolationsAllTenants(start, end);
                blockedBrowserEvents = browserEventRepository.countBlockedEventsAllTenants(start, end);
                browserSecurityEvents = browserEventRepository.countSecurityEventsAllTenants(start, end);

            } else {
                totalLogins = loginAuditRepository.countTotalLogins(tenantId, start, end);
                successfulLogins = loginAuditRepository.countSuccessfulLogins(tenantId, start, end);
                failedLogins = loginAuditRepository.countFailedLogins(tenantId, start, end);
                dailyTrends = buildDailyTrends(tenantId, start, end);
                loginsByHour = toIntMap(loginAuditRepository.getLoginsByHour(tenantId, start, end));
                failureReasons = toMap(loginAuditRepository.getFailureReasons(tenantId, start, end));

                securityEvents = loginAuditRepository.countSecurityEvents(tenantId, start, end);
                securityEventsByType = toMap(loginAuditRepository.getSecurityEventsByType(tenantId, start, end));
                accountLockedEvents = loginAuditRepository.countAccountLockedEvents(tenantId, start, end);
                mfaUsers = loginAuditRepository.countDistinctMfaUsers(tenantId, start, end);

                totalAuditEvents = loginAuditRepository.countTotalEvents(tenantId, start, end);
                activeDevices = loginAuditRepository.countActiveDevices(tenantId, start, end);
                devicesByType = toMap(loginAuditRepository.countDevicesByType(tenantId, start, end));
                auditEventsBySourceService = toMap(loginAuditRepository.getEventCountsBySourceService(tenantId, start, end));

                browserEvents = browserEventRepository.countByTenantIdAndTimeRange(tenantId, start, end);
                policyViolations = browserEventRepository.countPolicyViolations(tenantId, start, end);
                blockedBrowserEvents = browserEventRepository.countBlockedEvents(tenantId, start, end);
                browserSecurityEvents = browserEventRepository.countSecurityEvents(tenantId, start, end);
            }

            double successRate = totalLogins > 0
                    ? (double) successfulLogins / totalLogins * 100 : 0.0;

            return DateRangeStatsDTO.builder()
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalDays(totalDays)
                    .totalLogins(totalLogins)
                    .successfulLogins(successfulLogins)
                    .failedLogins(failedLogins)
                    .successRate(successRate)
                    .dailyLoginTrends(dailyTrends)
                    .loginsByHour(loginsByHour)
                    .failureReasons(failureReasons)
                    .securityEvents(securityEvents)
                    .securityEventsByType(securityEventsByType)
                    .accountLockedEvents(accountLockedEvents)
                    .mfaUsers(mfaUsers)
                    .totalAuditEvents(totalAuditEvents)
                    .activeDevices(activeDevices)
                    .devicesByType(devicesByType)
                    .auditEventsBySourceService(auditEventsBySourceService)
                    .browserEvents(browserEvents)
                    .policyViolations(policyViolations)
                    .blockedBrowserEvents(blockedBrowserEvents)
                    .browserSecurityEvents(browserSecurityEvents)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (GlobalException ge) {
            throw ge;
        } catch (Exception e) {
            log.error("Failed to get date range stats for tenant={} range=[{},{}]: {}",
                    tenantId, startDate, endDate, e.getMessage(), e);
            return DateRangeStatsDTO.empty(startDate, endDate, totalDays);
        }
    }

    /** Build daily login trend list for a specific tenant over any date range. */
    private List<LoginStatsDTO.DailyLoginTrend> buildDailyTrends(String tenantId,
                                                                    LocalDateTime start,
                                                                    LocalDateTime end) {
        try {
            return loginAuditRepository.getDailyLoginTrends(tenantId, start, end).stream()
                    .map(row -> LoginStatsDTO.DailyLoginTrend.builder()
                            .date(row[0].toString())
                            .successfulLogins(((Number) row[1]).longValue())
                            .failedLogins(((Number) row[2]).longValue())
                            .totalLogins(((Number) row[1]).longValue() + ((Number) row[2]).longValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to build daily trends for tenant {}: {}", tenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Build daily login trend list across all tenants over any date range. */
    private List<LoginStatsDTO.DailyLoginTrend> buildDailyTrendsAllTenants(LocalDateTime start,
                                                                             LocalDateTime end) {
        try {
            return loginAuditRepository.getDailyLoginTrendsAllTenants(start, end).stream()
                    .map(row -> LoginStatsDTO.DailyLoginTrend.builder()
                            .date(row[0].toString())
                            .successfulLogins(((Number) row[1]).longValue())
                            .failedLogins(((Number) row[2]).longValue())
                            .totalLogins(((Number) row[1]).longValue() + ((Number) row[2]).longValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to build daily trends for all tenants: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Convert Object[] rows (hour, count) to Map<Integer, Long>. */
    private Map<Integer, Long> toIntMap(List<Object[]> results) {
        Map<Integer, Long> map = new HashMap<>();
        if (results == null) return map;
        for (Object[] row : results) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            map.put(hour, count);
        }
        return map;
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private Tenant getTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }
}
