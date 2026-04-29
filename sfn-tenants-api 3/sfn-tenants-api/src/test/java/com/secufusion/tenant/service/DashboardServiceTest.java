package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.dashboard.*;
import com.secufusion.tenant.entity.BrowserDeviceStatus;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.exception.GlobalException;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private LoginAuditRepository loginAuditRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroupsRepository groupsRepository;
    @Mock private RolesRepository rolesRepository;
    @Mock private AuthService authService;
    @Mock private BrowserEventRepository browserEventRepository;
    @Mock private BrowserDeviceRepository browserDeviceRepository;
    @Mock private BrowserPolicyRepository browserPolicyRepository;
    @Mock private ExtensionPolicyRepository extensionPolicyRepository;
    @Mock private NetworkPolicyRepository networkPolicyRepository;
    @Mock private PolicyAssignmentRepository policyAssignmentRepository;
    @Mock private EventsGroupRepository eventsGroupRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Tenant tenant;
    private final String tenantId = "tenant-123";
    private final String parentTenantId = "parent-456";

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setTenantID(tenantId);
        tenant.setTenantName("Test Tenant");
        tenant.setRealmName("test-realm");
        tenant.setParentTenantId(parentTenantId);
        tenant.setTenantType("ENTERPRISE");
        tenant.setSelfManaged(false);
    }

    // ──────────────────────────────────────────────
    // canAccessTenantDashboard
    // ──────────────────────────────────────────────

    @Test
    void canAccessTenantDashboard_PlatformAdmin_ShouldReturnTrue() {
        // ARRANGE
        Tenant caller = new Tenant();
        caller.setTenantID("admin-id");
        caller.setTenantType("PLATFORM_ADMIN");
        caller.setParentTenantId(null);
        when(tenantRepository.findByTenantID("admin-id")).thenReturn(Optional.of(caller));

        // ACT
        boolean result = dashboardService.canAccessTenantDashboard("admin-id", tenantId);

        // ASSERT
        assertTrue(result);
        verify(tenantRepository, never()).findByTenantID(tenantId);
    }

    @Test
    void canAccessTenantDashboard_DirectParent_ShouldReturnTrue() {
        // ARRANGE
        Tenant caller = new Tenant();
        caller.setTenantID(parentTenantId);
        caller.setTenantType("MSSP");
        caller.setParentTenantId("grand-parent");
        when(tenantRepository.findByTenantID(parentTenantId)).thenReturn(Optional.of(caller));
        when(tenantRepository.findByTenantID(tenantId)).thenReturn(Optional.of(tenant));

        // ACT
        boolean result = dashboardService.canAccessTenantDashboard(parentTenantId, tenantId);

        // ASSERT
        assertTrue(result);
    }

    @Test
    void canAccessTenantDashboard_CallerNotFound_ShouldReturnFalse() {
        // ARRANGE
        when(tenantRepository.findByTenantID("unknown")).thenReturn(Optional.empty());

        // ACT
        boolean result = dashboardService.canAccessTenantDashboard("unknown", tenantId);

        // ASSERT
        assertFalse(result);
    }

    @Test
    void canAccessTenantDashboard_NotParent_ShouldReturnFalse() {
        // ARRANGE
        Tenant caller = new Tenant();
        caller.setTenantID("other-tenant");
        caller.setTenantType("MSSP");
        when(tenantRepository.findByTenantID("other-tenant")).thenReturn(Optional.of(caller));
        when(tenantRepository.findByTenantID(tenantId)).thenReturn(Optional.of(tenant));

        // ACT
        boolean result = dashboardService.canAccessTenantDashboard("other-tenant", tenantId);

        // ASSERT
        assertFalse(result);
    }

    // ──────────────────────────────────────────────
    // getDashboardOverview (covers multiple tenant types)
    // ──────────────────────────────────────────────

    // Helper method to set up consolidated login counts for getLoginStats
    private void setupConsolidatedLoginCountsAllTenants(long... values) {
        List<Object[]> rows = new ArrayList<>();
        Object[] row = new Object[9];
        for (int i = 0; i < values.length && i < 9; i++) {
            row[i] = values[i];
        }
        rows.add(row);
        when(loginAuditRepository.getConsolidatedLoginCountsAllTenants(any(), any(), any(), any()))
                .thenReturn(rows);
    }

    @Test
    void getDashboardOverview_PlatformAdmin_Success() {
        // ARRANGE
        Tenant platformTenant = new Tenant();
        platformTenant.setTenantID("plat");
        platformTenant.setTenantName("Platform");
        platformTenant.setTenantType("PLATFORM_ADMIN");
        platformTenant.setParentTenantId(null);
        platformTenant.setRealmName("master");
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platformTenant));

        // Tenant stats
        when(tenantRepository.countAllMssps()).thenReturn(5L);
        when(tenantRepository.countActiveMssps()).thenReturn(4L);
        when(tenantRepository.countAllEnterprises()).thenReturn(20L);
        when(tenantRepository.countActiveEnterprises()).thenReturn(18L);
        when(tenantRepository.countByTenantTypeAndStatus("ENTERPRISE", "PENDING")).thenReturn(2L);
        when(tenantRepository.countByTenantTypeAndStatus("MSSP", "SUSPENDED")).thenReturn(0L);
        when(tenantRepository.countByTenantTypeAndStatus("ENTERPRISE", "SUSPENDED")).thenReturn(1L);

        // User stats
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByStatus("ACTIVE")).thenReturn(80L);
        when(userRepository.countByStatus("INACTIVE")).thenReturn(15L);
        when(userRepository.countByStatus("LOCKED")).thenReturn(5L);
        when(userRepository.countByStatus("PENDING")).thenReturn(0L);

        // Session stats
        when(authService.getActiveSessionCount("plat")).thenReturn(10);
        when(authService.getSessionStats("plat")).thenReturn(Map.of("client1", 5L));

        // Login stats
        setupConsolidatedLoginCountsAllTenants(100L, 90L, 10L, 500L, 450L, 50L, 2000L, 1800L, 200L);
        List<Object[]> dailyTrendRows = new ArrayList<>();
        dailyTrendRows.add(new Object[]{"2026-04-28", 10, 2});
        when(loginAuditRepository.getDailyLoginTrendsAllTenants(any(), any())).thenReturn(dailyTrendRows);
        List<Object[]> hourRows = new ArrayList<>();
        hourRows.add(new Object[]{9, 15L});
        when(loginAuditRepository.getLoginsByHourAllTenants(any(), any())).thenReturn(hourRows);
        List<Object[]> failRows = new ArrayList<>();
        failRows.add(new Object[]{"INVALID_PASSWORD", 5L});
        when(loginAuditRepository.getFailureReasonsAllTenants(any(), any())).thenReturn(failRows);

        // Security stats
        when(loginAuditRepository.countAccountLockedEventsAllTenants(any(), any())).thenReturn(3L);
        List<Object[]> suspiciousIps = new ArrayList<>();
        suspiciousIps.add(new Object[]{"192.168.1.1", 12L});
        when(loginAuditRepository.findSuspiciousIpsAllTenants(any(LocalDateTime.class), anyLong()))
            .thenReturn(suspiciousIps);
        List<Object[]> secTypeRows = new ArrayList<>();
        secTypeRows.add(new Object[]{"BRUTE_FORCE", 7L});
        when(loginAuditRepository.getSecurityEventsByTypeAllTenants(any(), any())).thenReturn(secTypeRows);
        when(loginAuditRepository.countSecurityEventsAllTenants(any(), any())).thenReturn(7L);
        when(loginAuditRepository.countDistinctMfaUsersAllTenants(any(), any())).thenReturn(50L);

        // Policy stats
        when(browserPolicyRepository.count()).thenReturn(10L);
        when(browserPolicyRepository.countByIsActive(true)).thenReturn(8L);
        when(extensionPolicyRepository.count()).thenReturn(5L);
        when(extensionPolicyRepository.countByIsActive(true)).thenReturn(4L);
        when(networkPolicyRepository.count()).thenReturn(3L);
        when(networkPolicyRepository.countByIsActiveTrue()).thenReturn(2L);
        when(groupsRepository.count()).thenReturn(30L);
        when(policyAssignmentRepository.countDistinctResourcesByType("GROUP")).thenReturn(20L);
        when(browserPolicyRepository.countByCreatedAtAfter(any())).thenReturn(2L);
        when(browserPolicyRepository.countByUpdatedAtAfterAndCreatedAtBefore(any(), any())).thenReturn(1L);

        // Browser event stats
        List<Object[]> consolidatedEventCounts = new ArrayList<>();
        Object[] eventCounts = new Object[15];
        Arrays.fill(eventCounts, 0L);
        eventCounts[0] = 30L; // totalEventsToday
        eventCounts[12] = 100L; // activeUsersToday
        consolidatedEventCounts.add(eventCounts);
        when(browserEventRepository.getConsolidatedCountsAllTenants(any(), any(), any(), any()))
                .thenReturn(consolidatedEventCounts);
        when(browserEventRepository.countByEventTypeAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByCategoryAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByFileOperationTypeAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByThreatTypeAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countBySeverityAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopDomainsAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getDailyTrendsAllTenants(any(), any())).thenReturn(Collections.emptyList());
        List<Object[]> deviceCountRows = new ArrayList<>();
        Object[] deviceCounts = new Object[]{200L, 150L, 40L, 10L};
        deviceCountRows.add(deviceCounts);
        when(browserDeviceRepository.getConsolidatedDeviceCountsAllTenants()).thenReturn(deviceCountRows);
        when(browserDeviceRepository.countByDeviceTypeAllTenants()).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByBrowserTypeAllTenants()).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByOsInfoAllTenants()).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByExtensionVersionAllTenants()).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.findRecentDevicesAllTenants()).thenReturn(Collections.emptyList());

        // License stats
        // returns empty by default

        // ACT
        DashboardOverviewDTO result = dashboardService.getDashboardOverview("plat");

        // ASSERT
        assertNotNull(result);
        assertEquals("plat", result.getTenantId());
        assertNotNull(result.getTenantStats());
        assertNotNull(result.getUserStats());
        assertNotNull(result.getSessionStats());
        assertNotNull(result.getLoginStats());
        assertNotNull(result.getSecurityStats());
        assertNotNull(result.getPolicyStats());
        assertNotNull(result.getLicenseStats());
        assertNotNull(result.getDeviceStats());
        verify(tenantRepository).findById("plat");
    }

    @Test
    void getDashboardOverview_Enterprise_Success() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        // User stats
        when(userRepository.countByTenant_TenantID(tenantId)).thenReturn(50L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")).thenReturn(30L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "INACTIVE")).thenReturn(10L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "LOCKED")).thenReturn(5L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "PENDING")).thenReturn(5L);

        // Session
        when(authService.getActiveSessionCount(tenantId)).thenReturn(5);
        when(authService.getSessionStats(tenantId)).thenReturn(Map.of("web", 3L));

        // Login stats
        List<Object[]> loginCounts = new ArrayList<>();
        Object[] lc = new Object[9];
        lc[0] = 10L; lc[1] = 8L; lc[2] = 2L;
        loginCounts.add(lc);
        when(loginAuditRepository.getConsolidatedLoginCounts(eq(tenantId), any(), any(), any(), any()))
                .thenReturn(loginCounts);
        when(loginAuditRepository.getDailyLoginTrends(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getLoginsByHour(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getFailureReasons(anyString(), any(), any())).thenReturn(Collections.emptyList());

        // Security stats
        when(loginAuditRepository.countAccountLockedEvents(anyString(), any(), any())).thenReturn(0L);
        when(loginAuditRepository.findSuspiciousIps(anyString(), any(LocalDateTime.class), anyLong()))
            .thenReturn(Collections.emptyList());
        when(loginAuditRepository.getSecurityEventsByType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.countSecurityEvents(anyString(), any(), any())).thenReturn(0L);
        when(loginAuditRepository.countDistinctMfaUsers(anyString(), any(), any())).thenReturn(20L);

        // Policy stats
        when(browserPolicyRepository.countByFkTenantId(tenantId)).thenReturn(5L);
        when(browserPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true)).thenReturn(4L);
        when(extensionPolicyRepository.countByFkTenantId(tenantId)).thenReturn(3L);
        when(extensionPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true)).thenReturn(2L);
        when(networkPolicyRepository.countByFkTenantId(tenantId)).thenReturn(2L);
        when(networkPolicyRepository.countByFkTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
        when(groupsRepository.countByTenantId(tenantId)).thenReturn(10L);
        when(policyAssignmentRepository.countDistinctResourcesByTenantIdAndType(tenantId, "GROUP")).thenReturn(8L);
        when(browserPolicyRepository.countByFkTenantIdAndCreatedAtAfter(eq(tenantId), any())).thenReturn(1L);
        when(browserPolicyRepository.countByFkTenantIdAndUpdatedAtAfterAndCreatedAtBefore(eq(tenantId), any(), any())).thenReturn(0L);

        // Browser event stats
        List<Object[]> consolidatedEvents = new ArrayList<>();
        Object[] ev = new Object[15];
        ev[0] = 20L;
        consolidatedEvents.add(ev);
        when(browserEventRepository.getConsolidatedCounts(eq(tenantId), any(), any(), any(), any()))
                .thenReturn(consolidatedEvents);
        when(browserEventRepository.countByEventType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByCategory(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByFileOperationType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByThreatType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countBySeverity(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopDomains(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopUsers(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getDailyTrends(anyString(), any(), any())).thenReturn(Collections.emptyList());
        List<Object[]> devCounts = new ArrayList<>();
        devCounts.add(new Object[]{50L, 30L, 15L, 5L});
        when(browserDeviceRepository.getConsolidatedDeviceCounts(tenantId)).thenReturn(devCounts);
        when(browserDeviceRepository.countByDeviceType(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByBrowserType(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByOsInfo(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByExtensionVersion(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.findRecentDevices(tenantId)).thenReturn(Collections.emptyList());

        // ACT
        DashboardOverviewDTO result = dashboardService.getDashboardOverview(tenantId);

        // ASSERT
        assertNotNull(result);
        assertNull(result.getTenantStats()); // enterprise has none
        assertNotNull(result.getUserStats());
        assertNotNull(result.getDeviceStats());
    }

    @Test
    void getDashboardOverview_SelfManaged_ShouldIncludeMyOrgStats() {
        // ARRANGE
        tenant.setSelfManaged(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        // Minimal mock for enterprise dashboard
        mockEnterpriseDashboardMocks();
        // Self-managed own-org stats
        when(userRepository.countByTenant_TenantID(tenantId)).thenReturn(50L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")).thenReturn(30L);
        when(eventsGroupRepository.countByTenantIdAndIsActive(tenantId, true)).thenReturn(12L);
        when(eventsGroupRepository.countByTenantIdAndIsActiveAndAuthorized(tenantId, true, true)).thenReturn(10L);
        when(policyAssignmentRepository.countByTenantId(tenantId)).thenReturn(8L);
        when(browserDeviceRepository.countByTenantIdAndStatus(tenantId, BrowserDeviceStatus.ACTIVE)).thenReturn(25L);

        // ACT
        DashboardOverviewDTO result = dashboardService.getDashboardOverview(tenantId);

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isSelfManaged());
        assertNotNull(result.getMyOrgStats());
        assertEquals(50L, result.getMyOrgStats().getOwnUserCount());
        assertEquals(30L, result.getMyOrgStats().getActiveUserCount());
    }

    private void mockEnterpriseDashboardMocks() {
        // same as getDashboardOverview_Enterprise_Success but can be reused
        when(userRepository.countByTenant_TenantID(tenantId)).thenReturn(50L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")).thenReturn(30L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "INACTIVE")).thenReturn(10L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "LOCKED")).thenReturn(5L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "PENDING")).thenReturn(5L);
        when(authService.getActiveSessionCount(tenantId)).thenReturn(5);
        when(authService.getSessionStats(tenantId)).thenReturn(Map.of("web", 3L));
        List<Object[]> loginCounts = new ArrayList<>();
        Object[] lc = new Object[9];
        lc[0] = 10L; lc[1] = 8L; lc[2] = 2L;
        loginCounts.add(lc);
        when(loginAuditRepository.getConsolidatedLoginCounts(eq(tenantId), any(), any(), any(), any())).thenReturn(loginCounts);
        when(loginAuditRepository.getDailyLoginTrends(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getLoginsByHour(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getFailureReasons(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.countAccountLockedEvents(anyString(), any(), any())).thenReturn(0L);
        when(loginAuditRepository.findSuspiciousIps(anyString(), any(LocalDateTime.class), anyLong()))
            .thenReturn(Collections.emptyList());
        when(loginAuditRepository.getSecurityEventsByType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.countSecurityEvents(anyString(), any(), any())).thenReturn(0L);
        when(loginAuditRepository.countDistinctMfaUsers(anyString(), any(), any())).thenReturn(20L);
        when(browserPolicyRepository.countByFkTenantId(tenantId)).thenReturn(5L);
        when(browserPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true)).thenReturn(4L);
        when(extensionPolicyRepository.countByFkTenantId(tenantId)).thenReturn(3L);
        when(extensionPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true)).thenReturn(2L);
        when(networkPolicyRepository.countByFkTenantId(tenantId)).thenReturn(2L);
        when(networkPolicyRepository.countByFkTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
        when(groupsRepository.countByTenantId(tenantId)).thenReturn(10L);
        when(policyAssignmentRepository.countDistinctResourcesByTenantIdAndType(tenantId, "GROUP")).thenReturn(8L);
        when(browserPolicyRepository.countByFkTenantIdAndCreatedAtAfter(eq(tenantId), any())).thenReturn(1L);
        when(browserPolicyRepository.countByFkTenantIdAndUpdatedAtAfterAndCreatedAtBefore(eq(tenantId), any(), any())).thenReturn(0L);
        List<Object[]> consolidatedEvents = new ArrayList<>();
        Object[] ev = new Object[15];
        ev[0] = 20L;
        consolidatedEvents.add(ev);
        when(browserEventRepository.getConsolidatedCounts(eq(tenantId), any(), any(), any(), any()))
                .thenReturn(consolidatedEvents);
        when(browserEventRepository.countByEventType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByCategory(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByFileOperationType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByThreatType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countBySeverity(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopDomains(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopUsers(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getDailyTrends(anyString(), any(), any())).thenReturn(Collections.emptyList());
        List<Object[]> devCounts = new ArrayList<>();
        devCounts.add(new Object[]{50L, 30L, 15L, 5L});
        when(browserDeviceRepository.getConsolidatedDeviceCounts(tenantId)).thenReturn(devCounts);
        when(browserDeviceRepository.countByDeviceType(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByBrowserType(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByOsInfo(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByExtensionVersion(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.findRecentDevices(tenantId)).thenReturn(Collections.emptyList());
    }

    @Test
    void getDashboardOverview_TenantNotFound_ShouldThrowResourceNotFoundException() {
        // ARRANGE
        when(tenantRepository.findById("invalid")).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(ResourceNotFoundException.class, () ->
                dashboardService.getDashboardOverview("invalid"));
    }

    // ──────────────────────────────────────────────
    // getTenantStats
    // ──────────────────────────────────────────────
    @Test
    void getTenantStats_PlatformAdmin_ShouldReturnGlobalStats() {
        // ARRANGE
        Tenant platform = createPlatformAdmin();
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platform));
        when(tenantRepository.countAllMssps()).thenReturn(5L);
        when(tenantRepository.countActiveMssps()).thenReturn(4L);
        when(tenantRepository.countAllEnterprises()).thenReturn(20L);
        when(tenantRepository.countActiveEnterprises()).thenReturn(18L);
        when(tenantRepository.countByTenantTypeAndStatus("ENTERPRISE", "PENDING")).thenReturn(2L);
        when(tenantRepository.countByTenantTypeAndStatus("MSSP", "SUSPENDED")).thenReturn(0L);
        when(tenantRepository.countByTenantTypeAndStatus("ENTERPRISE", "SUSPENDED")).thenReturn(1L);

        // ACT
        TenantStatsDTO result = dashboardService.getTenantStats("plat");

        // ASSERT
        assertNotNull(result);
        assertEquals(5, result.getTotalMssps());
    }

    @Test
    void getTenantStats_Enterprise_ShouldReturnNull() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        // ACT
        TenantStatsDTO result = dashboardService.getTenantStats(tenantId);

        // ASSERT
        assertNull(result);
    }

    @Test
    void getTenantStats_MasterMssp_ShouldReturnScopedStats() {
        // ARRANGE
        Tenant master = new Tenant();
        master.setTenantID("master-1");
        master.setTenantType("MASTER_MSSP");
        master.setParentTenantId(null);
        when(tenantRepository.findById("master-1")).thenReturn(Optional.of(master));
        when(tenantRepository.countMsspsByParentTenantId("master-1")).thenReturn(3L);
        when(tenantRepository.countActiveMsspsByParentTenantId("master-1")).thenReturn(2L);
        when(tenantRepository.countEnterprisesUnderMasterMssp("master-1")).thenReturn(10L);
        when(tenantRepository.countActiveEnterprisesUnderMasterMssp("master-1")).thenReturn(8L);
        when(tenantRepository.countPendingEnterprisesUnderMasterMssp("master-1")).thenReturn(2L);
        when(tenantRepository.countSuspendedTenantsUnderMasterMssp("master-1")).thenReturn(1L);

        // ACT
        TenantStatsDTO result = dashboardService.getTenantStats("master-1");

        // ASSERT
        assertNotNull(result);
        assertEquals(3, result.getTotalMssps());
    }

    // ──────────────────────────────────────────────
    // getUserStats
    // ──────────────────────────────────────────────
    @Test
    void getUserStats_PlatformAdmin_ShouldReturnAggregated() {
        // ARRANGE
        Tenant platform = createPlatformAdmin();
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platform));
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByStatus("ACTIVE")).thenReturn(80L);
        when(userRepository.countByStatus("INACTIVE")).thenReturn(15L);
        when(userRepository.countByStatus("LOCKED")).thenReturn(5L);
        when(userRepository.countByStatus("PENDING")).thenReturn(0L);

        // ACT
        UserStatsDTO result = dashboardService.getUserStats("plat");

        // ASSERT
        assertNotNull(result);
        assertEquals(100L, result.getTotalUsers());
    }

    @Test
    void getUserStats_Exception_ShouldReturnEmpty() {
        // ARRANGE
        Tenant platform = createPlatformAdmin();
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platform));
        when(userRepository.count()).thenThrow(new RuntimeException("DB error"));

        // ACT
        UserStatsDTO result = dashboardService.getUserStats("plat");

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getTotalUsers());
    }

    // ──────────────────────────────────────────────
    // getSessionStats
    // ──────────────────────────────────────────────
    @Test
    void getSessionStats_Success() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(authService.getActiveSessionCount(tenantId)).thenReturn(10);
        when(authService.getSessionStats(tenantId)).thenReturn(Map.of("app", 5L));

        // ACT
        SessionStatsDTO result = dashboardService.getSessionStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(10, result.getActiveSessions());
        assertEquals(Map.of("app", 5L), result.getSessionsByClient());
    }

    @Test
    void getSessionStats_NoRealm_ShouldReturnEmpty() {
        // ARRANGE
        tenant.setRealmName(null);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        // ACT
        SessionStatsDTO result = dashboardService.getSessionStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getActiveSessions());
    }

    @Test
    void getSessionStats_AuthException_ShouldReturnEmpty() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(authService.getActiveSessionCount(tenantId)).thenThrow(new RuntimeException("Auth down"));

        // ACT
        SessionStatsDTO result = dashboardService.getSessionStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getActiveSessions());
    }

    // ──────────────────────────────────────────────
    // getLoginStats
    // ──────────────────────────────────────────────
    @Test
    void getLoginStats_PlatformAdmin_Success() {
        // ARRANGE
        Tenant platform = createPlatformAdmin();
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platform));
        setupConsolidatedLoginCountsAllTenants(100L, 90L, 10L, 500L, 450L, 50L, 2000L, 1800L, 200L);
        when(loginAuditRepository.getDailyLoginTrendsAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getLoginsByHourAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getFailureReasonsAllTenants(any(), any())).thenReturn(Collections.emptyList());

        // ACT
        LoginStatsDTO result = dashboardService.getLoginStats("plat");

        // ASSERT
        assertNotNull(result);
        assertEquals(100L, result.getLoginsToday());
        assertEquals(90.0, result.getSuccessRateToday(), 0.01);
    }

    @Test
    void getLoginStats_Exception_ShouldReturnEmpty() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(loginAuditRepository.getConsolidatedLoginCounts(anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB fail"));

        // ACT
        LoginStatsDTO result = dashboardService.getLoginStats(tenantId);

        // ASSERT
        assertNotNull(result);
        // LoginStatsDTO.empty() should have default values
        assertEquals(0L, result.getLoginsToday());
    }

    // ──────────────────────────────────────────────
    // getSecurityStats
    // ──────────────────────────────────────────────
    @Test
    void getSecurityStats_Success() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(loginAuditRepository.countAccountLockedEvents(anyString(), any(), any())).thenReturn(5L);
        List<Object[]> suspiciousIps = new ArrayList<>();
        suspiciousIps.add(new Object[]{"10.0.0.1", 12L});
        when(loginAuditRepository.findSuspiciousIps(anyString(), any(LocalDateTime.class), anyLong()))
            .thenReturn(suspiciousIps);
        List<Object[]> secType = new ArrayList<>();
        secType.add(new Object[]{"BRUTE_FORCE", 7L});
        when(loginAuditRepository.getSecurityEventsByType(anyString(), any(), any())).thenReturn(secType);
        when(loginAuditRepository.countSecurityEvents(anyString(), any(), any())).thenReturn(7L);
        when(loginAuditRepository.countDistinctMfaUsers(anyString(), any(), any())).thenReturn(30L);
        when(userRepository.countByTenant_TenantID(tenantId)).thenReturn(100L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "LOCKED")).thenReturn(2L);

        // ACT
        SecurityStatsDTO result = dashboardService.getSecurityStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(1, result.getBlockedIPs());
        assertEquals(5, result.getTemporarilyLockedUsers());
        assertEquals(30L, result.getMfaEnabledUsers());
    }

    @Test
    void getSecurityStats_Exception_ShouldReturnEmpty() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(loginAuditRepository.countAccountLockedEvents(anyString(), any(), any()))
                .thenThrow(new RuntimeException("fail"));

        // ACT
        SecurityStatsDTO result = dashboardService.getSecurityStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getBlockedIPs());
    }

    // ──────────────────────────────────────────────
    // getPolicyStats
    // ──────────────────────────────────────────────
    @Test
    void getPolicyStats_Success() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(browserPolicyRepository.countByFkTenantId(tenantId)).thenReturn(5L);
        when(browserPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true)).thenReturn(4L);
        when(extensionPolicyRepository.countByFkTenantId(tenantId)).thenReturn(3L);
        when(extensionPolicyRepository.countByFkTenantIdAndIsActive(tenantId, true)).thenReturn(2L);
        when(networkPolicyRepository.countByFkTenantId(tenantId)).thenReturn(2L);
        when(networkPolicyRepository.countByFkTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
        when(groupsRepository.countByTenantId(tenantId)).thenReturn(10L);
        when(policyAssignmentRepository.countDistinctResourcesByTenantIdAndType(tenantId, "GROUP")).thenReturn(8L);
        when(browserPolicyRepository.countByFkTenantIdAndCreatedAtAfter(eq(tenantId), any())).thenReturn(1L);
        when(browserPolicyRepository.countByFkTenantIdAndUpdatedAtAfterAndCreatedAtBefore(eq(tenantId), any(), any())).thenReturn(0L);

        // ACT
        PolicyStatsDTO result = dashboardService.getPolicyStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(10, result.getTotalBrowserPolicies());
    }

    @Test
    void getPolicyStats_Exception_ShouldReturnEmpty() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(browserPolicyRepository.countByFkTenantId(tenantId)).thenThrow(new RuntimeException("fail"));

        // ACT
        PolicyStatsDTO result = dashboardService.getPolicyStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getTotalBrowserPolicies());
    }

    // ──────────────────────────────────────────────
    // getLicenseStats (currently returns empty)
    // ──────────────────────────────────────────────
    @Test
    void getLicenseStats_PlatformAdmin_ShouldReturnEmpty() {
        // ARRANGE
        Tenant platform = createPlatformAdmin();
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platform));

        // ACT
        LicenseStatsDTO result = dashboardService.getLicenseStats("plat");

        // ASSERT
        assertNotNull(result);
        // empty() returns a builder with null fields
        assertEquals(0L, result.getTotalLicenses());
    }

    @Test
    void getLicenseStats_Enterprise_ShouldReturnNull() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        // ACT
        LicenseStatsDTO result = dashboardService.getLicenseStats(tenantId);

        // ASSERT
        assertNull(result);
    }

    // ──────────────────────────────────────────────
    // getTenantDashboardStats
    // ──────────────────────────────────────────────
    @Test
    void getTenantDashboardStats_Enterprise_Success() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        when(loginAuditRepository.countActiveDevices(eq(tenantId), any(), any())).thenReturn(10L);
        List<Object[]> devicesByType = new ArrayList<>();
        devicesByType.add(new Object[]{"Desktop", 5L});
        when(loginAuditRepository.countDevicesByType(anyString(), any(), any())).thenReturn(devicesByType);

        when(loginAuditRepository.countTotalEvents(anyString(), any(), any())).thenReturn(100L);
        when(loginAuditRepository.countSecurityEvents(anyString(), any(), any())).thenReturn(10L);
        List<Object[]> secType = new ArrayList<>();
        secType.add(new Object[]{"BRUTE_FORCE", 5L});
        when(loginAuditRepository.getSecurityEventsByType(anyString(), any(), any())).thenReturn(secType);

        // organization
        when(userRepository.countByTenant_TenantID(tenantId)).thenReturn(50L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")).thenReturn(30L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "INACTIVE")).thenReturn(10L);
        when(userRepository.countByTenantIdAndStatus(tenantId, "LOCKED")).thenReturn(5L);
        when(groupsRepository.countByTenantId(tenantId)).thenReturn(12L);
        when(rolesRepository.countByTenant_TenantID(tenantId)).thenReturn(8L);

        // recent users
        List<Object[]> recentUsers = new ArrayList<>();
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(1));
        recentUsers.add(new Object[]{"user-1", "john", "john@test.com", ts, "192.168.1.1", "US", "Mozilla/5.0", true});
        when(loginAuditRepository.findRecentUserLogins(tenantId, 10)).thenReturn(recentUsers);

        // recent devices
        List<Object[]> recentDevices = new ArrayList<>();
        recentDevices.add(new Object[]{"dev-1", "Chrome", "10.0.0.1", "US", ts, "john", 5L});
        when(loginAuditRepository.findRecentDevices(eq(tenantId), any(), eq(10))).thenReturn(recentDevices);

        // browser event stats
        List<Object[]> consolidatedEvents = new ArrayList<>();
        Object[] ev = new Object[15];
        ev[0] = 20L;
        consolidatedEvents.add(ev);
        when(browserEventRepository.getConsolidatedCounts(eq(tenantId), any(), any(), any(), any()))
                .thenReturn(consolidatedEvents);
        when(browserEventRepository.countByEventType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByCategory(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByFileOperationType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countByThreatType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.countBySeverity(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopDomains(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getTopUsers(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(browserEventRepository.getDailyTrends(anyString(), any(), any())).thenReturn(Collections.emptyList());
        List<Object[]> deviceCounts = new ArrayList<>();
        deviceCounts.add(new Object[]{50L, 30L, 15L, 5L});
        when(browserDeviceRepository.getConsolidatedDeviceCounts(tenantId)).thenReturn(deviceCounts);
        when(browserDeviceRepository.countByDeviceType(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByBrowserType(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByOsInfo(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.countByExtensionVersion(tenantId)).thenReturn(Collections.emptyList());
        when(browserDeviceRepository.findRecentDevices(tenantId)).thenReturn(Collections.emptyList());

        // ACT
        TenantDashboardStatsDTO result = dashboardService.getTenantDashboardStats(tenantId);

        // ASSERT
        assertNotNull(result);
        assertEquals(10L, result.getActiveDevices());
        assertEquals(1, result.getDevicesByType().size());
        assertEquals(1, result.getRecentUsers().size());
        assertEquals("john", result.getRecentUsers().get(0).getUsername());
        assertEquals("Unknown", result.getRecentUsers().get(0).getDeviceType());
    }

    @Test
    void getTenantDashboardStats_Exception_ShouldReturnEmpty() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(loginAuditRepository.countActiveDevices(anyString(), any(), any()))
                .thenThrow(new RuntimeException("fail"));

        // ACT
        TenantDashboardStatsDTO result = dashboardService.getTenantDashboardStats(tenantId);

        // ASSERT
        assertNotNull(result);
        // empty() should have null fields
        assertEquals(0L, result.getActiveDevices());
    }

    // ──────────────────────────────────────────────
    // getDateRangeStats
    // ──────────────────────────────────────────────
    @Test
    void getDateRangeStats_InvalidRange_StartAfterEnd_ShouldThrowGlobalException() {
        // ARRANGE
        // ACT + ASSERT
        assertThrows(GlobalException.class, () ->
                dashboardService.getDateRangeStats(tenantId, LocalDate.now(), LocalDate.now().minusDays(1)));
    }

    @Test
    void getDateRangeStats_RangeExceedsLimit_ShouldThrowGlobalException() {
        // ARRANGE
        // ACT + ASSERT
        assertThrows(GlobalException.class, () ->
                dashboardService.getDateRangeStats(tenantId, LocalDate.now().minusDays(367), LocalDate.now()));
    }

    @Test
    void getDateRangeStats_Enterprise_Success() {
        // ARRANGE
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        when(loginAuditRepository.countTotalLogins(anyString(), any(), any())).thenReturn(100L);
        when(loginAuditRepository.countSuccessfulLogins(anyString(), any(), any())).thenReturn(80L);
        when(loginAuditRepository.countFailedLogins(anyString(), any(), any())).thenReturn(20L);
        when(loginAuditRepository.getDailyLoginTrends(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getLoginsByHour(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getFailureReasons(anyString(), any(), any())).thenReturn(Collections.emptyList());

        when(loginAuditRepository.countSecurityEvents(anyString(), any(), any())).thenReturn(5L);
        when(loginAuditRepository.getSecurityEventsByType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.countAccountLockedEvents(anyString(), any(), any())).thenReturn(2L);
        when(loginAuditRepository.countDistinctMfaUsers(anyString(), any(), any())).thenReturn(10L);

        when(loginAuditRepository.countTotalEvents(anyString(), any(), any())).thenReturn(200L);
        when(loginAuditRepository.countActiveDevices(anyString(), any(), any())).thenReturn(15L);
        when(loginAuditRepository.countDevicesByType(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getEventCountsBySourceService(anyString(), any(), any())).thenReturn(Collections.emptyList());

        when(browserEventRepository.countByTenantIdAndTimeRange(anyString(), any(), any())).thenReturn(50L);
        when(browserEventRepository.countPolicyViolations(anyString(), any(), any())).thenReturn(10L);
        when(browserEventRepository.countBlockedEvents(anyString(), any(), any())).thenReturn(5L);
        when(browserEventRepository.countSecurityEvents(anyString(), any(), any())).thenReturn(3L);

        // ACT
        DateRangeStatsDTO result = dashboardService.getDateRangeStats(tenantId, start, end);

        // ASSERT
        assertNotNull(result);
        assertEquals(100L, result.getTotalLogins());
        assertEquals(80.0, result.getSuccessRate(), 0.01);
        assertEquals(50L, result.getBrowserEvents());
        assertEquals(start, result.getStartDate());
        assertEquals(end, result.getEndDate());
    }

    @Test
    void getDateRangeStats_PlatformAdmin_Success() {
        // ARRANGE
        LocalDate start = LocalDate.now().minusDays(5);
        LocalDate end = LocalDate.now();
        Tenant platform = createPlatformAdmin();
        when(tenantRepository.findById("plat")).thenReturn(Optional.of(platform));

        when(loginAuditRepository.countTotalLoginsAllTenants(any(), any())).thenReturn(500L);
        when(loginAuditRepository.countSuccessfulLoginsAllTenants(any(), any())).thenReturn(450L);
        when(loginAuditRepository.countFailedLoginsAllTenants(any(), any())).thenReturn(50L);
        when(loginAuditRepository.getDailyLoginTrendsAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getLoginsByHourAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.getFailureReasonsAllTenants(any(), any())).thenReturn(Collections.emptyList());

        when(loginAuditRepository.countSecurityEventsAllTenants(any(), any())).thenReturn(30L);
        when(loginAuditRepository.getSecurityEventsByTypeAllTenants(any(), any())).thenReturn(Collections.emptyList());
        when(loginAuditRepository.countAccountLockedEventsAllTenants(any(), any())).thenReturn(5L);
        when(loginAuditRepository.countDistinctMfaUsersAllTenants(any(), any())).thenReturn(100L);

        when(loginAuditRepository.countTotalEventsAllTenants(any(), any())).thenReturn(1000L);
        when(loginAuditRepository.countActiveDevicesAllTenants(any(), any())).thenReturn(200L);
        when(loginAuditRepository.countDevicesByTypeAllTenants(any(), any())).thenReturn(Collections.emptyList());

        when(browserEventRepository.countAllByTimeRange(any(), any())).thenReturn(300L);
        when(browserEventRepository.countPolicyViolationsAllTenants(any(), any())).thenReturn(50L);
        when(browserEventRepository.countBlockedEventsAllTenants(any(), any())).thenReturn(20L);
        when(browserEventRepository.countSecurityEventsAllTenants(any(), any())).thenReturn(15L);

        // ACT
        DateRangeStatsDTO result = dashboardService.getDateRangeStats("plat", start, end);

        // ASSERT
        assertNotNull(result);
        assertEquals(500L, result.getTotalLogins());
        assertEquals(300L, result.getBrowserEvents());
    }

    @Test
    void getDateRangeStats_Exception_ShouldReturnEmpty() {
        // ARRANGE
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(loginAuditRepository.countTotalLogins(anyString(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        // ACT
        DateRangeStatsDTO result = dashboardService.getDateRangeStats(tenantId, LocalDate.now().minusDays(1), LocalDate.now());

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getTotalLogins());
    }

    // ──────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────
    private Tenant createPlatformAdmin() {
        Tenant t = new Tenant();
        t.setTenantID("plat");
        t.setTenantName("Platform");
        t.setTenantType("PLATFORM_ADMIN");
        t.setParentTenantId(null);
        t.setRealmName("master");
        return t;
    }


}