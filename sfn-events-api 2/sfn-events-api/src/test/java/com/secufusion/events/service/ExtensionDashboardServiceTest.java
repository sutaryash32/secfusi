package com.secufusion.events.service;

import com.secufusion.events.dto.ExtensionDashboardDTO;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.ExtensionEventRepository;
import com.secufusion.events.repository.InstalledExtensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtensionDashboardService Tests")
class ExtensionDashboardServiceTest {

    @Mock private InstalledExtensionRepository extensionRepository;
    @Mock private ExtensionEventRepository     eventRepository;

    @InjectMocks
    private ExtensionDashboardService service;

    private static final String TENANT_ID    = "tenant-001";
    private static final String EXTENSION_ID = "ext-aaa";

    // ── Row builders ──────────────────────────────────────────────────────────

    private Object[] inventoryRow(String extId, String name, String risk,
                                  String policy, long installs) {
        return new Object[]{
                extId,
                name,
                "1.0.0",
                "publisher",
                risk,
                50,
                "category",
                policy,
                installs,
                installs,
                1L,
                1L,
                Timestamp.valueOf(LocalDateTime.now().minusDays(5)),
                Timestamp.valueOf(LocalDateTime.now()),
                false,
                false
        };
    }

    private ExtensionEvent buildEvent(String id, ExtensionEventType type) {
        ExtensionEvent e = new ExtensionEvent();
        e.setPkExtensionEventId(id);
        e.setExtensionId(EXTENSION_ID);
        e.setExtensionName("Test Extension");
        e.setEventType(type);
        e.setEventDescription("desc");
        e.setUserName("user@example.com");
        e.setPolicyAction("ALLOW");
        e.setRiskLevel("LOW");
        e.setEventTimestamp(LocalDateTime.now());
        e.setDevice(null);
        return e;
    }

    private InstalledExtension buildInstalled(String extId, String name,
                                              String status, String risk) {
        InstalledExtension ie = new InstalledExtension();
        ie.setExtensionId(extId);
        ie.setExtensionName(name);
        ie.setVersion("1.0.0");
        ie.setRiskLevel(risk);
        ie.setRiskScore(30);
        ie.setPolicyAction("ALLOW");
        ie.setUserId("user-001");
        ie.setInstalledAt(LocalDateTime.now().minusDays(3));
        ie.setLastSeenAt(LocalDateTime.now());
        ie.setStatus(status != null ? ExtensionStatus.valueOf(status) : null);
        ie.setIsWhitelisted(false);
        ie.setIsBlacklisted(false);
        ie.setPermissions(List.of("storage"));
        ie.setHostPermissions(List.of("<all_urls>"));
        ie.setHighRiskPermissions(List.of("nativeMessaging"));
        ie.setDevice(null);
        return ie;
    }

    // ── Shared stubs ──────────────────────────────────────────────────────────

    private void stubOverviewBase() {
        lenient().when(extensionRepository.countUniqueExtensions(TENANT_ID)).thenReturn(5L);
        lenient().when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "ALLOW")).thenReturn(3L);
        lenient().when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "WARN")).thenReturn(1L);
        lenient().when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "BLOCK")).thenReturn(1L);

        List<Object[]> riskRows = new ArrayList<>();
        riskRows.add(new Object[]{"HIGH", 2L});
        riskRows.add(new Object[]{"LOW",  3L});
        lenient().when(extensionRepository.countByRiskLevel(TENANT_ID)).thenReturn(riskRows);

        List<Object[]> policyRows = new ArrayList<>();
        policyRows.add(new Object[]{"ALLOW", 3L});
        lenient().when(extensionRepository.countByPolicyAction(TENANT_ID)).thenReturn(policyRows);

        List<Object[]> statusRows = new ArrayList<>();
        statusRows.add(new Object[]{"ACTIVE", 4L});
        lenient().when(extensionRepository.countByStatus(TENANT_ID)).thenReturn(statusRows);

        lenient().when(eventRepository.countByTenantIdAndEventType(TENANT_ID,
                ExtensionEventType.EXTENSION_INSTALLED)).thenReturn(10L);
        lenient().when(eventRepository.countByTenantIdAndEventType(TENANT_ID,
                ExtensionEventType.EXTENSION_UNINSTALLED)).thenReturn(2L);

        List<Object[]> eventTypeRows = new ArrayList<>();
        eventTypeRows.add(new Object[]{"EXTENSION_INSTALLED",   8L});
        eventTypeRows.add(new Object[]{"EXTENSION_UNINSTALLED", 1L});
        lenient().when(eventRepository.countEventsByType(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(eventTypeRows);

        lenient().when(eventRepository.countBlockEventsSince(eq(TENANT_ID), any())).thenReturn(5L);
        lenient().when(eventRepository.countWarningEventsSince(eq(TENANT_ID), any())).thenReturn(3L);
        lenient().when(eventRepository.countWarningAcknowledgedSince(eq(TENANT_ID), any())).thenReturn(2L);
        lenient().when(eventRepository.countByTenantIdAndEventTimestampAfter(eq(TENANT_ID), any()))
                .thenReturn(8L);

        List<Object[]> invRows = new ArrayList<>();
        invRows.add(inventoryRow(EXTENSION_ID, "Risky Ext", "HIGH", "WARN", 4L));
        lenient().when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(invRows);

        // ✅ FIX 1: List.of(entity) → ArrayList
        List<ExtensionEvent> recentList = new ArrayList<>();
        recentList.add(buildEvent("ev-001", ExtensionEventType.EXTENSION_INSTALLED));
        lenient().when(eventRepository.findRecentEvents(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(recentList);
    }

    @BeforeEach
    void setUp() {
        stubOverviewBase();
    }

    // =========================================================================
    // getOverview
    // =========================================================================

    @Nested
    @DisplayName("getOverview")
    class GetOverview {

        @Test
        @DisplayName("Happy Path — full overview returned with all fields populated")
        void happyPath_fullOverview() {
            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            assertNotNull(result);
            assertEquals(5L, result.getTotalUniqueExtensions());
            assertEquals(4L, result.getActiveInstallations());
            assertNotNull(result.getRiskDistribution());
            assertFalse(result.getRiskDistribution().isEmpty());
            assertNotNull(result.getPolicyDistribution());
            assertNotNull(result.getStatusDistribution());
            assertNotNull(result.getLast30Days());
            assertNotNull(result.getTopHighRiskExtensions());
            assertNotNull(result.getRecentEvents());
            assertFalse(result.getRecentEvents().isEmpty());

            verify(extensionRepository).countUniqueExtensions(TENANT_ID);
            verify(extensionRepository).countByRiskLevel(TENANT_ID);
            verify(extensionRepository).countByPolicyAction(TENANT_ID);
            verify(extensionRepository).countByStatus(TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — null risk/policy rows use default keys")
        void happyPath_nullKeysInRows_useDefaultKeys() {
            List<Object[]> nullKeyRisk = new ArrayList<>();
            nullKeyRisk.add(new Object[]{null, 2L});
            when(extensionRepository.countByRiskLevel(TENANT_ID)).thenReturn(nullKeyRisk);

            List<Object[]> nullKeyPolicy = new ArrayList<>();
            nullKeyPolicy.add(new Object[]{null, 3L});
            when(extensionRepository.countByPolicyAction(TENANT_ID)).thenReturn(nullKeyPolicy);

            List<Object[]> nullKeyStatus = new ArrayList<>();
            nullKeyStatus.add(new Object[]{null, 1L});
            when(extensionRepository.countByStatus(TENANT_ID)).thenReturn(nullKeyStatus);

            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            assertNotNull(result);
            assertTrue(result.getRiskDistribution().containsKey("NONE"));
            assertTrue(result.getPolicyDistribution().containsKey("ALLOW"));
            assertTrue(result.getStatusDistribution().containsKey("UNKNOWN"));
        }

        @Test
        @DisplayName("Happy Path — totalInstallations 0 falls back to statusDistribution sum")
        void happyPath_zeroTotalInstallations_usesStatusSum() {
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "ALLOW")).thenReturn(0L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "WARN")).thenReturn(0L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "BLOCK")).thenReturn(0L);

            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            assertNotNull(result);
            assertEquals(4L, result.getTotalInstallations());
        }

        @Test
        @DisplayName("Happy Path — countByTenantIdAndEventTimestampAfter=0 gives 0 newInstallations")
        void happyPath_noRecentTimestampEvents_newInstallationsZero() {
            when(eventRepository.countByTenantIdAndEventTimestampAfter(eq(TENANT_ID), any()))
                    .thenReturn(0L);

            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            assertNotNull(result);
            assertEquals(0L, result.getLast30Days().getNewInstallations());
        }

        @Test
        @DisplayName("Happy Path — CRITICAL risk extensions included in topHighRisk")
        void happyPath_criticalRiskExtension_includedInTopHighRisk() {
            List<Object[]> criticalInv = new ArrayList<>();
            criticalInv.add(inventoryRow("ext-crit", "CritExt", "CRITICAL", "BLOCK", 10L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(criticalInv);

            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            assertFalse(result.getTopHighRiskExtensions().isEmpty());
            assertEquals("CRITICAL", result.getTopHighRiskExtensions().get(0).getRiskLevel());
        }

        @Test
        @DisplayName("Happy Path — recentEvents limited to 10 even with more entities")
        void happyPath_recentEventsLimitedToTen() {
            List<ExtensionEvent> manyEvents = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                manyEvents.add(buildEvent("ev-" + i, ExtensionEventType.EXTENSION_INSTALLED));
            }
            when(eventRepository.findRecentEvents(eq(TENANT_ID), any())).thenReturn(manyEvents);

            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            assertTrue(result.getRecentEvents().size() <= 10);
        }

        @Test
        @DisplayName("Happy Path — event with null device gives null deviceName in RecentEvent")
        void happyPath_eventWithNullDevice_nullDeviceName() {
            // ARRANGE
            ExtensionEvent evt = buildEvent("ev-null", ExtensionEventType.EXTENSION_BLOCKED);
            evt.setDevice(null);
            evt.setEventType(null);

            // ✅ FIX 2: List.of(evt) → ArrayList
            List<ExtensionEvent> evtList = new ArrayList<>();
            evtList.add(evt);
            when(eventRepository.findRecentEvents(eq(TENANT_ID), any())).thenReturn(evtList);

            ExtensionDashboardDTO.Overview result = service.getOverview(TENANT_ID);

            ExtensionDashboardDTO.RecentEvent recent = result.getRecentEvents().get(0);
            assertNull(recent.getDeviceName());
            assertNull(recent.getEventType());
        }
    }

    // =========================================================================
    // getInventory
    // =========================================================================

    @Nested
    @DisplayName("getInventory")
    class GetInventory {

        @Test
        @DisplayName("Happy Path — no filters returns all items paged")
        void happyPath_noFilters_returnsAllPaged() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(inventoryRow("ext-1", "Ext One",   "LOW",    "ALLOW", 5L));
            rows.add(inventoryRow("ext-2", "Ext Two",   "HIGH",   "BLOCK", 2L));
            rows.add(inventoryRow("ext-3", "Ext Three", "MEDIUM", "WARN",  3L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, null, null, null, 0, 10);

            assertNotNull(result);
            assertEquals(3, result.getTotalElements());
            assertEquals(3, result.getContent().size());
        }

        @Test
        @DisplayName("Happy Path — riskLevel filter applies correctly")
        void happyPath_riskLevelFilter() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(inventoryRow("ext-1", "Ext One", "LOW",  "ALLOW", 5L));
            rows.add(inventoryRow("ext-2", "Ext Two", "HIGH", "BLOCK", 2L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, "HIGH", null, null, 0, 10);

            assertEquals(1, result.getTotalElements());
            assertEquals("HIGH", result.getContent().get(0).getRiskLevel());
        }

        @Test
        @DisplayName("Happy Path — policyAction filter applies correctly")
        void happyPath_policyActionFilter() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(inventoryRow("ext-1", "Ext One", "LOW",  "ALLOW", 5L));
            rows.add(inventoryRow("ext-2", "Ext Two", "HIGH", "BLOCK", 2L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, null, "BLOCK", null, 0, 10);

            assertEquals(1, result.getTotalElements());
            assertEquals("BLOCK", result.getContent().get(0).getPolicyAction());
        }

        @Test
        @DisplayName("Happy Path — search by name filters correctly")
        void happyPath_searchByName() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(inventoryRow("ext-1", "AdBlocker", "LOW", "ALLOW", 3L));
            rows.add(inventoryRow("ext-2", "Grammarly",  "LOW", "ALLOW", 2L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, null, null, "adblock", 0, 10);

            assertEquals(1, result.getTotalElements());
            assertEquals("AdBlocker", result.getContent().get(0).getExtensionName());
        }

        @Test
        @DisplayName("Happy Path — page beyond results returns empty content")
        void happyPath_pageBeyondResults_emptyContent() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(inventoryRow("ext-1", "Ext One", "LOW", "ALLOW", 5L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, null, null, null, 5, 10);

            assertTrue(result.getContent().isEmpty());
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Happy Path — blank search term returns all items")
        void happyPath_blankSearch_returnsAll() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(inventoryRow("ext-1", "Ext One", "LOW",  "ALLOW", 5L));
            rows.add(inventoryRow("ext-2", "Ext Two", "HIGH", "BLOCK", 2L));
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, null, null, "   ", 0, 10);

            assertEquals(2, result.getTotalElements());
        }

        @Test
        @DisplayName("Happy Path — isWhitelisted/isBlacklisted flags mapped correctly")
        void happyPath_whitelistedBlacklistedFlags() {
            // ARRANGE
            Object[] row = inventoryRow("ext-1", "Ext One", "LOW", "ALLOW", 5L);
            row[14] = true;
            row[15] = true;

            // ✅ FIX 3: List.of(row) → ArrayList
            List<Object[]> singleRow = new ArrayList<>();
            singleRow.add(row);
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(singleRow);

            Page<ExtensionDashboardDTO.InventoryItem> result =
                    service.getInventory(TENANT_ID, null, null, null, 0, 10);

            ExtensionDashboardDTO.InventoryItem item = result.getContent().get(0);
            // ✅ FIX 4: IsWhitelisted() → isWhitelisted() (Lombok @Data generates isX() for boolean)
            assertTrue(item.isWhitelisted());
            assertTrue(item.isBlacklisted());
        }
    }

    // =========================================================================
    // getExtensionDetail
    // =========================================================================

    @Nested
    @DisplayName("getExtensionDetail")
    class GetExtensionDetail {

        @Test
        @DisplayName("Happy Path — active installation chosen as representative")
        void happyPath_activeInstallationAsRep() {
            InstalledExtension active   = buildInstalled(EXTENSION_ID, "TestExt", "ACTIVE",   "HIGH");
            InstalledExtension inactive = buildInstalled(EXTENSION_ID, "TestExt", "DISABLED", "LOW");

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(inactive, active));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(eq(TENANT_ID), eq(EXTENSION_ID),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertNotNull(result);
            assertEquals(EXTENSION_ID, result.getExtensionId());
            assertEquals("TestExt",   result.getExtensionName());
            assertEquals("HIGH",      result.getRiskLevel());
            assertEquals(2,           result.getTotalInstallations());
            assertEquals(1L,          result.getActiveInstallations());
        }

        @Test
        @DisplayName("Happy Path — no ACTIVE installation uses first as representative")
        void happyPath_noActiveInstallation_usesFirst() {
            InstalledExtension ie = buildInstalled(EXTENSION_ID, "TestExt", "DISABLED", "MEDIUM");

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(ie));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(eq(TENANT_ID), eq(EXTENSION_ID),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertNotNull(result);
            assertEquals("MEDIUM", result.getRiskLevel());
        }

        @Test
        @DisplayName("Happy Path — permissions merged across all installations")
        void happyPath_permissionsMerged() {
            InstalledExtension ie1 = buildInstalled(EXTENSION_ID, "Ext", "ACTIVE",   "LOW");
            ie1.setPermissions(List.of("storage", "tabs"));
            ie1.setHighRiskPermissions(Collections.emptyList());

            InstalledExtension ie2 = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", "LOW");
            ie2.setPermissions(List.of("tabs", "cookies"));
            ie2.setHighRiskPermissions(Collections.emptyList());

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(ie1, ie2));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertNotNull(result.getPermissions());
            assertTrue(result.getPermissions().containsAll(List.of("storage", "tabs", "cookies")));
            assertEquals(3, result.getPermissions().size());
        }

        @Test
        @DisplayName("Happy Path — version history rows mapped correctly")
        void happyPath_versionHistoryMapped() {
            InstalledExtension ie = buildInstalled(EXTENSION_ID, "Ext", "ACTIVE", "LOW");
            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(ie));

            List<Object[]> verRows = new ArrayList<>();
            verRows.add(new Object[]{
                    EXTENSION_ID,
                    "1.2.0",
                    Timestamp.valueOf(LocalDateTime.now().minusDays(2)),
                    3L
            });
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(verRows);
            when(eventRepository.getTenantExtensionHistory(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertFalse(result.getVersionHistory().isEmpty());
            assertEquals("1.2.0", result.getVersionHistory().get(0).getVersion());
            assertEquals(3L,      result.getVersionHistory().get(0).getDeviceCount());
        }

        @Test
        @DisplayName("Happy Path — anyWhitelisted and anyBlacklisted flags set correctly")
        void happyPath_whitelistedBlacklistedFlags() {
            InstalledExtension ie = buildInstalled(EXTENSION_ID, "Ext", "ACTIVE", "LOW");
            ie.setIsWhitelisted(true);
            ie.setIsBlacklisted(true);

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(ie));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            // ✅ FIX 5: getIsWhitelisted() → isWhitelisted() (Lombok boolean getter)
            assertTrue(result.isWhitelisted());
            assertTrue(result.isBlacklisted());
        }

        @Test
        @DisplayName("Happy Path — highRiskPermissions null when all empty")
        void happyPath_emptyHighRiskPermissions_nullInResult() {
            InstalledExtension ie = buildInstalled(EXTENSION_ID, "Ext", "ACTIVE", "LOW");
            ie.setHighRiskPermissions(Collections.emptyList());

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(ie));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertNull(result.getHighRiskPermissions());
        }

        @Test
        @DisplayName("Sad Path — no installations throws ResourceNotFoundException")
        void sadPath_noInstallations_throwsNotFound() {
            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.emptyList());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.getExtensionDetail(TENANT_ID, EXTENSION_ID));
        }
    }

    // =========================================================================
    // getTrends
    // =========================================================================

    @Nested
    @DisplayName("getTrends")
    class GetTrends {

        @Test
        @DisplayName("Happy Path — daily activity, topNew and topRemoved all populated")
        void happyPath_allSectionsFilled() {
            List<Object[]> dailyRows = new ArrayList<>();
            dailyRows.add(new Object[]{Date.valueOf(LocalDate.now()), 3L, 1L, 2L, 1L});
            when(eventRepository.getDailyActivityBreakdown(eq(TENANT_ID), any()))
                    .thenReturn(dailyRows);

            // ✅ FIX 6: List.of(invRow) → ArrayList
            Object[] invRow = inventoryRow(EXTENSION_ID, "NewExt", "LOW", "ALLOW", 5L);
            invRow[12] = Timestamp.valueOf(LocalDateTime.now().minusDays(1));
            List<Object[]> invList = new ArrayList<>();
            invList.add(invRow);
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(invList);

            List<Object[]> removedRows = new ArrayList<>();
            removedRows.add(new Object[]{EXTENSION_ID, "OldExt", 3L});
            when(eventRepository.getTopRemovedExtensions(eq(TENANT_ID), any(), eq(10)))
                    .thenReturn(removedRows);

            ExtensionDashboardDTO.Trends result = service.getTrends(TENANT_ID, 30);

            assertNotNull(result);
            assertEquals(1, result.getDailyActivity().size());
            assertEquals(3L, result.getDailyActivity().get(0).getInstalled());
            assertEquals(1, result.getTopNewExtensions().size());
            assertEquals(1, result.getTopRemovedExtensions().size());
            assertEquals("OldExt", result.getTopRemovedExtensions().get(0).getExtensionName());
        }

        @Test
        @DisplayName("Happy Path — toLocalDate java.util.Date branch covered")
        void happyPath_utilDateBranch() {
            List<Object[]> dailyRows = new ArrayList<>();
            dailyRows.add(new Object[]{new java.util.Date(), 1L, 0L, 0L, 0L});
            when(eventRepository.getDailyActivityBreakdown(eq(TENANT_ID), any()))
                    .thenReturn(dailyRows);
            when(extensionRepository.getExtensionInventory(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            when(eventRepository.getTopRemovedExtensions(any(), any(), anyInt()))
                    .thenReturn(Collections.<Object[]>emptyList());

            ExtensionDashboardDTO.Trends result = service.getTrends(TENANT_ID, 7);

            assertNotNull(result);
            assertEquals(1, result.getDailyActivity().size());
            assertNotNull(result.getDailyActivity().get(0).getDate());
        }

        @Test
        @DisplayName("Happy Path — LocalDate in daily row[0] branch covered")
        void happyPath_localDateBranch() {
            List<Object[]> dailyRows = new ArrayList<>();
            dailyRows.add(new Object[]{LocalDate.now(), 2L, 1L, 0L, 0L});
            when(eventRepository.getDailyActivityBreakdown(eq(TENANT_ID), any()))
                    .thenReturn(dailyRows);
            when(extensionRepository.getExtensionInventory(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            when(eventRepository.getTopRemovedExtensions(any(), any(), anyInt()))
                    .thenReturn(Collections.<Object[]>emptyList());

            ExtensionDashboardDTO.Trends result = service.getTrends(TENANT_ID, 7);

            assertNotNull(result);
            assertNotNull(result.getDailyActivity().get(0).getDate());
        }

        @Test
        @DisplayName("Happy Path — null date in daily row returns null LocalDate")
        void happyPath_nullDateInDailyRow_returnsNull() {
            List<Object[]> dailyRows = new ArrayList<>();
            dailyRows.add(new Object[]{null, 1L, 0L, 0L, 0L});
            when(eventRepository.getDailyActivityBreakdown(eq(TENANT_ID), any()))
                    .thenReturn(dailyRows);
            when(extensionRepository.getExtensionInventory(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            when(eventRepository.getTopRemovedExtensions(any(), any(), anyInt()))
                    .thenReturn(Collections.<Object[]>emptyList());

            ExtensionDashboardDTO.Trends result = service.getTrends(TENANT_ID, 7);

            assertNull(result.getDailyActivity().get(0).getDate());
        }

        @Test
        @DisplayName("Happy Path — inventory item firstSeenAt before window excluded from topNew")
        void happyPath_oldInventoryItem_excludedFromTopNew() {
            // ✅ FIX 7: List.of(oldRow) → ArrayList
            Object[] oldRow = inventoryRow("ext-old", "OldExt", "LOW", "ALLOW", 5L);
            oldRow[12] = Timestamp.valueOf(LocalDateTime.now().minusDays(60));
            List<Object[]> oldList = new ArrayList<>();
            oldList.add(oldRow);

            when(eventRepository.getDailyActivityBreakdown(eq(TENANT_ID), any()))
                    .thenReturn(Collections.emptyList());
            when(extensionRepository.getExtensionInventory(TENANT_ID)).thenReturn(oldList);
            when(eventRepository.getTopRemovedExtensions(any(), any(), anyInt()))
                    .thenReturn(Collections.<Object[]>emptyList());

            ExtensionDashboardDTO.Trends result = service.getTrends(TENANT_ID, 7);

            assertTrue(result.getTopNewExtensions().isEmpty());
        }
    }

    // =========================================================================
    // getUserRiskProfiles
    // =========================================================================

    @Nested
    @DisplayName("getUserRiskProfiles")
    class GetUserRiskProfiles {

        @Test
        @DisplayName("Happy Path — profiles paged and risk levels assigned correctly")
        void happyPath_profilesPagedWithRiskLevels() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"du-1","user1","u1@x.com","User One",  10L, 2L, 1L, 80, Timestamp.valueOf(LocalDateTime.now())});
            rows.add(new Object[]{"du-2","user2","u2@x.com","User Two",  5L,  0L, 0L, 20, Timestamp.valueOf(LocalDateTime.now())});
            rows.add(new Object[]{"du-3","user3","u3@x.com","User Three",8L,  1L, 0L, 55, null});
            when(extensionRepository.getUserExtensionRiskProfiles(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.UserRiskProfile> result =
                    service.getUserRiskProfiles(TENANT_ID, 0, 10);

            assertNotNull(result);
            assertEquals(3, result.getTotalElements());
            assertEquals("du-1",     result.getContent().get(0).getDeviceUserId());
            assertEquals("Critical", result.getContent().get(0).getRiskLevel());
            assertEquals("Low",      result.getContent().get(1).getRiskLevel());
            assertEquals("High",     result.getContent().get(2).getRiskLevel());
            assertNull(result.getContent().get(2).getLastActivityAt());
        }

        @Test
        @DisplayName("Happy Path — getRiskLevel boundary: score=50 → High, score=75 → Critical")
        void happyPath_riskLevelBoundaries() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"du-a","u","e","d", 1L, 0L, 0L, 75, null});
            rows.add(new Object[]{"du-b","u","e","d", 1L, 0L, 0L, 50, null});
            rows.add(new Object[]{"du-c","u","e","d", 1L, 0L, 0L, 25, null});
            rows.add(new Object[]{"du-d","u","e","d", 1L, 0L, 0L, 24, null});
            when(extensionRepository.getUserExtensionRiskProfiles(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.UserRiskProfile> result =
                    service.getUserRiskProfiles(TENANT_ID, 0, 10);

            assertEquals("Critical", result.getContent().get(0).getRiskLevel());
            assertEquals("High",     result.getContent().get(1).getRiskLevel());
            assertEquals("Medium",   result.getContent().get(2).getRiskLevel());
            assertEquals("Low",      result.getContent().get(3).getRiskLevel());
        }

        @Test
        @DisplayName("Happy Path — page beyond results returns empty content")
        void happyPath_pageBeyondResults_emptyContent() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"du-1","u","e","d",1L,0L,0L,10,null});
            when(extensionRepository.getUserExtensionRiskProfiles(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.UserRiskProfile> result =
                    service.getUserRiskProfiles(TENANT_ID, 5, 10);

            assertTrue(result.getContent().isEmpty());
        }

        @Test
        @DisplayName("Happy Path — toLocalDateTime LocalDateTime branch covered")
        void happyPath_toLocalDateTimeLocalDateTimeBranch() {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{"du-1","u","e","d",1L,0L,0L,10,LocalDateTime.now()});
            when(extensionRepository.getUserExtensionRiskProfiles(TENANT_ID)).thenReturn(rows);

            Page<ExtensionDashboardDTO.UserRiskProfile> result =
                    service.getUserRiskProfiles(TENANT_ID, 0, 10);

            assertNotNull(result.getContent().get(0).getLastActivityAt());
        }
    }

    // =========================================================================
    // getPolicyEffectiveness
    // =========================================================================

    @Nested
    @DisplayName("getPolicyEffectiveness")
    class GetPolicyEffectiveness {

        @Test
        @DisplayName("Happy Path — event-based blocked/warned rows used when present")
        void happyPath_eventRowsUsed() {
            when(eventRepository.countBlockEventsSince(eq(TENANT_ID), any())).thenReturn(10L);
            when(eventRepository.countWarningEventsSince(eq(TENANT_ID), any())).thenReturn(6L);
            when(eventRepository.countWarningAcknowledgedSince(eq(TENANT_ID), any())).thenReturn(3L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "ALLOW")).thenReturn(5L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "BLOCK")).thenReturn(2L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "WARN")).thenReturn(1L);
            when(extensionRepository.countWhitelistedExtensions(TENANT_ID)).thenReturn(2L);
            when(extensionRepository.countBlacklistedExtensions(TENANT_ID)).thenReturn(1L);

            List<Object[]> blockedRows = new ArrayList<>();
            blockedRows.add(new Object[]{"ext-1", "Ext One", 5L, 2L});
            when(eventRepository.getMostBlockedWithUsers(eq(TENANT_ID), any(), eq(10)))
                    .thenReturn(blockedRows);

            List<Object[]> warnedRows = new ArrayList<>();
            warnedRows.add(new Object[]{"ext-2", "Ext Two", 4L, 2L});
            when(eventRepository.getMostWarnedExtensions(eq(TENANT_ID), any(), eq(10)))
                    .thenReturn(warnedRows);

            ExtensionDashboardDTO.PolicyEffectiveness result =
                    service.getPolicyEffectiveness(TENANT_ID, 30);

            assertNotNull(result);
            assertEquals(5L,   result.getAllowedCount());
            assertEquals(2L,   result.getWhitelistedCount());
            assertEquals(1L,   result.getBlacklistedCount());
            assertEquals(50.0, result.getWarningAcknowledgeRate());
            assertFalse(result.getTopBlockedExtensions().isEmpty());
            assertFalse(result.getTopWarnedExtensions().isEmpty());
            verify(eventRepository, times(1)).getMostBlockedWithUsers(eq(TENANT_ID), any(), eq(10));
            verify(eventRepository, times(1)).getMostWarnedExtensions(eq(TENANT_ID), any(), eq(10));
        }

        @Test
        @DisplayName("Happy Path — fallback to installedExtensions when event rows empty")
        void happyPath_fallbackToInstalledExtensionsRows() {
            when(eventRepository.countBlockEventsSince(any(), any())).thenReturn(0L);
            when(eventRepository.countWarningEventsSince(any(), any())).thenReturn(0L);
            when(eventRepository.countWarningAcknowledgedSince(any(), any())).thenReturn(0L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "ALLOW")).thenReturn(3L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "BLOCK")).thenReturn(1L);
            when(extensionRepository.countByTenantIdAndPolicyAction(TENANT_ID, "WARN")).thenReturn(1L);
            when(extensionRepository.countWhitelistedExtensions(TENANT_ID)).thenReturn(0L);
            when(extensionRepository.countBlacklistedExtensions(TENANT_ID)).thenReturn(0L);
            when(eventRepository.getMostBlockedWithUsers(any(), any(), anyInt()))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getMostWarnedExtensions(any(), any(), anyInt()))
                    .thenReturn(Collections.<Object[]>emptyList());

            List<Object[]> fallbackBlocked = new ArrayList<>();
            fallbackBlocked.add(new Object[]{"ext-b", "BlockedExt", 3L, 1L});
            when(extensionRepository.getTopBlockedByPolicy(TENANT_ID, 10))
                    .thenReturn(fallbackBlocked);

            List<Object[]> fallbackWarned = new ArrayList<>();
            fallbackWarned.add(new Object[]{"ext-w", "WarnedExt", 2L, 1L});
            when(extensionRepository.getTopWarnedByPolicy(TENANT_ID, 10))
                    .thenReturn(fallbackWarned);

            ExtensionDashboardDTO.PolicyEffectiveness result =
                    service.getPolicyEffectiveness(TENANT_ID, 30);

            assertNotNull(result);
            assertEquals("BlockedExt", result.getTopBlockedExtensions().get(0).getExtensionName());
            assertEquals("WarnedExt",  result.getTopWarnedExtensions().get(0).getExtensionName());
            assertEquals(0.0, result.getWarningAcknowledgeRate());
        }
    }

    // =========================================================================
    // executeBulkAction
    // =========================================================================

    @Nested
    @DisplayName("executeBulkAction")
    class ExecuteBulkAction {

        private ExtensionDashboardDTO.BulkActionRequest buildRequest(String action) {
            ExtensionDashboardDTO.BulkActionRequest req =
                    new ExtensionDashboardDTO.BulkActionRequest();
            req.setAction(action);
            req.setExtensionIds(List.of("ext-1", "ext-2"));
            req.setReason("Test reason");
            return req;
        }

        @Test
        @DisplayName("Happy Path — WHITELIST action delegates to bulkWhitelist")
        void happyPath_whitelist() {
            when(extensionRepository.bulkWhitelist(eq(TENANT_ID), anyList(), anyString()))
                    .thenReturn(2);

            ExtensionDashboardDTO.BulkActionResult result =
                    service.executeBulkAction(TENANT_ID, buildRequest("WHITELIST"));

            assertNotNull(result);
            assertEquals(2,           result.getUpdatedCount());
            assertEquals(2,           result.getTotalRequested());
            assertEquals("WHITELIST", result.getAction());
            verify(extensionRepository).bulkWhitelist(eq(TENANT_ID), anyList(), anyString());
        }

        @Test
        @DisplayName("Happy Path — BLACKLIST action delegates to bulkBlacklist")
        void happyPath_blacklist() {
            when(extensionRepository.bulkBlacklist(eq(TENANT_ID), anyList(), anyString()))
                    .thenReturn(2);

            ExtensionDashboardDTO.BulkActionResult result =
                    service.executeBulkAction(TENANT_ID, buildRequest("BLACKLIST"));

            assertEquals("BLACKLIST", result.getAction());
            verify(extensionRepository).bulkBlacklist(eq(TENANT_ID), anyList(), anyString());
        }

        @Test
        @DisplayName("Happy Path — BLOCK action delegates to bulkUpdatePolicyAction")
        void happyPath_block() {
            when(extensionRepository.bulkUpdatePolicyAction(
                    eq(TENANT_ID), anyList(), eq("BLOCK"), anyString())).thenReturn(2);

            ExtensionDashboardDTO.BulkActionResult result =
                    service.executeBulkAction(TENANT_ID, buildRequest("BLOCK"));

            assertEquals("BLOCK", result.getAction());
        }

        @Test
        @DisplayName("Happy Path — WARN action delegates to bulkUpdatePolicyAction")
        void happyPath_warn() {
            when(extensionRepository.bulkUpdatePolicyAction(
                    eq(TENANT_ID), anyList(), eq("WARN"), anyString())).thenReturn(1);

            ExtensionDashboardDTO.BulkActionResult result =
                    service.executeBulkAction(TENANT_ID, buildRequest("WARN"));

            assertEquals("WARN", result.getAction());
        }

        @Test
        @DisplayName("Happy Path — ALLOW action delegates to bulkUpdatePolicyAction")
        void happyPath_allow() {
            when(extensionRepository.bulkUpdatePolicyAction(
                    eq(TENANT_ID), anyList(), eq("ALLOW"), anyString())).thenReturn(2);

            ExtensionDashboardDTO.BulkActionResult result =
                    service.executeBulkAction(TENANT_ID, buildRequest("ALLOW"));

            assertEquals("ALLOW", result.getAction());
        }

        @Test
        @DisplayName("Happy Path — null reason defaults to built-in message")
        void happyPath_nullReason_usesDefaultMessage() {
            ExtensionDashboardDTO.BulkActionRequest req = buildRequest("WHITELIST");
            req.setReason(null);
            when(extensionRepository.bulkWhitelist(eq(TENANT_ID), anyList(),
                    eq("Bulk action from dashboard"))).thenReturn(2);

            ExtensionDashboardDTO.BulkActionResult result =
                    service.executeBulkAction(TENANT_ID, req);

            assertNotNull(result);
            verify(extensionRepository).bulkWhitelist(eq(TENANT_ID), anyList(),
                    eq("Bulk action from dashboard"));
        }

        @Test
        @DisplayName("Sad Path — unknown action throws IllegalArgumentException")
        void sadPath_unknownAction_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.executeBulkAction(TENANT_ID, buildRequest("INVALID_ACTION")));
        }
    }

    // =========================================================================
    // riskOrd / policyOrd
    // =========================================================================

    @Nested
    @DisplayName("riskOrd and policyOrd — all branches via getExtensionDetail")
    class RiskAndPolicyOrd {

        @Test
        @DisplayName("riskOrd — CRITICAL > HIGH > MEDIUM > LOW > null selects CRITICAL as max")
        void riskOrd_allValues_criticalWins() {
            InstalledExtension low      = buildInstalled(EXTENSION_ID, "Ext", "ACTIVE",   "LOW");
            InstalledExtension medium   = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", "MEDIUM");
            InstalledExtension high     = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", "HIGH");
            InstalledExtension critical = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", "CRITICAL");
            InstalledExtension nullRisk = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", null);
            nullRisk.setRiskLevel(null);

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(low, medium, high, critical, nullRisk));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertEquals("CRITICAL", result.getRiskLevel());
        }

        @Test
        @DisplayName("policyOrd — BLOCK > WARN > ALLOW selects BLOCK as strictest")
        void policyOrd_allValues_blockWins() {
            InstalledExtension allow = buildInstalled(EXTENSION_ID, "Ext", "ACTIVE",   "LOW");
            allow.setPolicyAction("ALLOW");
            InstalledExtension warn  = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", "LOW");
            warn.setPolicyAction("WARN");
            InstalledExtension block = buildInstalled(EXTENSION_ID, "Ext", "DISABLED", "LOW");
            block.setPolicyAction("BLOCK");

            when(extensionRepository.findByTenantIdAndExtensionIdOrderByLastSeenAtDesc(
                    TENANT_ID, EXTENSION_ID))
                    .thenReturn(List.of(allow, warn, block));
            when(extensionRepository.getVersionHistory(TENANT_ID, EXTENSION_ID))
                    .thenReturn(Collections.<Object[]>emptyList());
            when(eventRepository.getTenantExtensionHistory(any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            ExtensionDashboardDTO.ExtensionDetail result =
                    service.getExtensionDetail(TENANT_ID, EXTENSION_ID);

            assertEquals("BLOCK", result.getPolicyAction());
        }
    }
}