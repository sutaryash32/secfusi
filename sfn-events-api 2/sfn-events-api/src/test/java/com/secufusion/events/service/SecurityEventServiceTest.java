package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SecurityEventService Tests")
class SecurityEventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private IncidentEventRepository incidentEventRepository;

    @InjectMocks
    private SecurityEventService service;

    private static final String TENANT_ID = "t1";
    private static final String DEVICE_ID = "d1";

    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        // Build a typical Event object for reuse
        sampleEvent = new Event();
        sampleEvent.setPkEventId("ev-1");
        sampleEvent.setTenant(new Tenant());
        sampleEvent.getTenant().setTenantID(TENANT_ID);
        sampleEvent.setUrl("http://example.com");
        sampleEvent.setTimeStamp(LocalDateTime.now());
        sampleEvent.setUserName("user1");
        sampleEvent.setDevice(new Device());
        sampleEvent.getDevice().setDeviceId(DEVICE_ID);
        sampleEvent.getDevice().setDeviceName("Test Device");
        sampleEvent.setEventType(EventType.EXTENSION_VIOLATION);
        sampleEvent.setSeverity("high");
        sampleEvent.setThreatType("malware");
        sampleEvent.setRiskLevel("High");
        sampleEvent.setActionTaken("blocked");
        sampleEvent.setPolicyName("Default Policy");
        sampleEvent.setIsSecurityEvent(true);

        // Default lenient stubs for incident mapping (empty links)
        lenient().when(incidentEventRepository.findByEventIdsAndTenantId(anyList(), eq(TENANT_ID)))
                .thenReturn(Collections.emptyList());
    }

    // ==================== getSecurityEvents (basic) ====================
    @Nested
    @DisplayName("getSecurityEvents")
    class GetSecurityEventsTests {

        @Test
        @DisplayName("Happy Path – returns paged results")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueOrderByTimeStampDesc(
                    eq(TENANT_ID), any(Pageable.class))).thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEvents(TENANT_ID, 0, 10);
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            SecurityEventDTO dto = result.getContent().get(0);
            assertEquals("ev-1", dto.getEventId());
            assertEquals("high", dto.getSeverity());
        }
    }

    // ==================== getSecurityEventsWithFilters ====================
    @Nested
    @DisplayName("getSecurityEventsWithFilters")
    class GetSecurityEventsWithFiltersTests {

        @Test
        @DisplayName("Happy Path – filters applied")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findSecurityEventsWithFilters(
                    eq(TENANT_ID), eq("high"), eq("malware"), eq("High"), eq("blocked"),
                    any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEventsWithFilters(
                    TENANT_ID, "high", "malware", "High", "blocked",
                    LocalDateTime.now().minusDays(7), LocalDateTime.now(), 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getSecurityEventsBySeverity ====================
    @Nested
    @DisplayName("getSecurityEventsBySeverity")
    class GetSecurityEventsBySeverityTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndSeverityOrderByTimeStampDesc(
                    eq(TENANT_ID), eq("high"), any(Pageable.class))).thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEventsBySeverity(TENANT_ID, "high", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getSecurityEventsByThreatType ====================
    @Nested
    @DisplayName("getSecurityEventsByThreatType")
    class GetSecurityEventsByThreatTypeTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndThreatTypeOrderByTimeStampDesc(
                    eq(TENANT_ID), eq("malware"), any(Pageable.class))).thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEventsByThreatType(TENANT_ID, "malware", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getSecurityEventsByRiskLevel ====================
    @Nested
    @DisplayName("getSecurityEventsByRiskLevel")
    class GetSecurityEventsByRiskLevelTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndRiskLevelOrderByTimeStampDesc(
                    eq(TENANT_ID), eq("High"), any(Pageable.class))).thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEventsByRiskLevel(TENANT_ID, "High", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getSecurityEventsByUser ====================
    @Nested
    @DisplayName("getSecurityEventsByUser")
    class GetSecurityEventsByUserTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndUserNameOrderByTimeStampDesc(
                    eq(TENANT_ID), eq("user1"), any(Pageable.class))).thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEventsByUser(TENANT_ID, "user1", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getSecurityEventsByDevice ====================
    @Nested
    @DisplayName("getSecurityEventsByDevice")
    class GetSecurityEventsByDeviceTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByDevice_DeviceIdAndIsSecurityEventTrueOrderByTimeStampDesc(
                    eq(DEVICE_ID), any(Pageable.class))).thenReturn(page);

            Page<SecurityEventDTO> result = service.getSecurityEventsByDevice(DEVICE_ID, 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== getSecurityEventsByTimeRange ====================
    @Nested
    @DisplayName("getSecurityEventsByTimeRange")
    class GetSecurityEventsByTimeRangeTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(eventRepository.findByTenant_TenantIDAndIsSecurityEventTrueAndTimeStampBetweenOrderByTimeStampDesc(
                    eq(TENANT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(sampleEvent));

            List<SecurityEventDTO> result = service.getSecurityEventsByTimeRange(
                    TENANT_ID, LocalDateTime.now().minusDays(7), LocalDateTime.now());
            assertEquals(1, result.size());
        }
    }

    // ==================== getCriticalSecurityEvents ====================
    @Nested
    @DisplayName("getCriticalSecurityEvents")
    class GetCriticalSecurityEventsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(eventRepository.findCriticalSecurityEvents(eq(TENANT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(sampleEvent));

            List<SecurityEventDTO> result = service.getCriticalSecurityEvents(
                    TENANT_ID, LocalDateTime.now().minusDays(7), LocalDateTime.now());
            assertEquals(1, result.size());
        }
    }

    // ==================== getSecurityEventStats ====================
    @Nested
    @DisplayName("getSecurityEventStats")
    class GetSecurityEventStatsTests {

        @Test
        @DisplayName("Happy Path – all stats returned with Object[] rows")
        void happyPath() {
            LocalDateTime start = LocalDateTime.now().minusDays(7);
            LocalDateTime end = LocalDateTime.now();

            // Stub counts
            lenient().when(eventRepository.countSecurityEventsByTimeRange(TENANT_ID, start, end)).thenReturn(100L);

            // Severity rows
            List<Object[]> severityRows = new ArrayList<>();
            severityRows.add(new Object[]{"critical", 10L});
            severityRows.add(new Object[]{"high", 30L});
            severityRows.add(new Object[]{"medium", 40L});
            severityRows.add(new Object[]{"low", 20L});
            when(eventRepository.countSecurityEventsBySeverity(TENANT_ID, start, end)).thenReturn(severityRows);

            // Threat type rows
            List<Object[]> threatRows = new ArrayList<>();
            threatRows.add(new Object[]{"malware", 50L});
            when(eventRepository.countSecurityEventsByThreatType(TENANT_ID, start, end)).thenReturn(threatRows);

            // Risk level rows
            List<Object[]> riskRows = new ArrayList<>();
            riskRows.add(new Object[]{"High", 60L});
            when(eventRepository.countSecurityEventsByRiskLevel(TENANT_ID, start, end)).thenReturn(riskRows);

            // Action taken rows
            List<Object[]> actionRows = new ArrayList<>();
            actionRows.add(new Object[]{"blocked", 70L});
            when(eventRepository.countSecurityEventsByActionTaken(TENANT_ID, start, end)).thenReturn(actionRows);

            // Policy type rows
            List<Object[]> policyRows = new ArrayList<>();
            policyRows.add(new Object[]{"security", 80L});
            when(eventRepository.countSecurityEventsByPolicyType(TENANT_ID, start, end)).thenReturn(policyRows);

            // Top users rows (Object[])
            List<Object[]> topUserRows = new ArrayList<>();
            topUserRows.add(new Object[]{"user1", 25L});
            when(eventRepository.getTopUsersWithSecurityEvents(eq(TENANT_ID), any(), any(), any(Pageable.class)))
                    .thenReturn(topUserRows);

            // Top threat types rows
            List<Object[]> topThreatRows = new ArrayList<>();
            topThreatRows.add(new Object[]{"malware", 50L});
            when(eventRepository.getTopThreatTypes(eq(TENANT_ID), any(), any(), any(Pageable.class)))
                    .thenReturn(topThreatRows);

            // Daily trends rows
            List<Object[]> dailyRows = new ArrayList<>();
            dailyRows.add(new Object[]{"2025-01-01", 15L, 2L, 5L, 6L, 2L});
            when(eventRepository.getDailySecurityEventTrends(TENANT_ID, start, end)).thenReturn(dailyRows);

            // Critical events for recent critical
            when(eventRepository.findCriticalSecurityEvents(TENANT_ID, start, end))
                    .thenReturn(List.of(sampleEvent));

            // ACT
            SecurityEventStatsDTO stats = service.getSecurityEventStats(TENANT_ID, start, end);

            // ASSERT
            assertNotNull(stats);
            assertEquals(100L, stats.getTotalSecurityEvents());
            assertEquals(10L, stats.getCriticalCount());
            assertEquals(30L, stats.getHighCount());
            assertEquals(40L, stats.getMediumCount());
            assertEquals(20L, stats.getLowCount());

            // Check maps
            assertEquals(10L, stats.getBySeverity().get("critical"));
            assertEquals(50L, stats.getByThreatType().get("malware"));
            assertEquals(60L, stats.getByRiskLevel().get("High"));
            assertEquals(70L, stats.getByActionTaken().get("blocked"));
            assertEquals(80L, stats.getByPolicyType().get("security"));

            // Top users
            assertEquals(1, stats.getTopUsersWithSecurityEvents().size());
            assertEquals("user1", stats.getTopUsersWithSecurityEvents().get(0).getUserName());
            assertEquals(25L, stats.getTopUsersWithSecurityEvents().get(0).getEventCount());

            // Top threats
            assertEquals(1, stats.getTopThreatTypes().size());
            assertEquals("malware", stats.getTopThreatTypes().get(0).getThreatType());

            // Daily trends
            assertEquals(1, stats.getDailyTrends().size());
            assertEquals(15L, stats.getDailyTrends().get(0).getTotalEvents());

            // Recent critical
            assertEquals(1, stats.getRecentCriticalEvents().size());
        }
    }

    // ==================== countSecurityEvents ====================
    @Nested
    @DisplayName("countSecurityEvents")
    class CountSecurityEventsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(eventRepository.countSecurityEvents(TENANT_ID)).thenReturn(42L);
            assertEquals(42L, service.countSecurityEvents(TENANT_ID));
        }
    }

    // ==================== countSecurityEventsByTimeRange ====================
    @Nested
    @DisplayName("countSecurityEventsByTimeRange")
    class CountSecurityEventsByTimeRangeTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now();
            when(eventRepository.countSecurityEventsByTimeRange(TENANT_ID, start, end)).thenReturn(10L);
            assertEquals(10L, service.countSecurityEventsByTimeRange(TENANT_ID, start, end));
        }
    }

    // ==================== getSecurityDashboard ====================
    @Nested
    @DisplayName("getSecurityDashboard")
    class GetSecurityDashboardTests {

        @Test
        @DisplayName("Happy Path – dashboard with summary, breakdown, pagination")
        void happyPath() {
            LocalDateTime start = LocalDateTime.now().minusDays(7);
            LocalDateTime end = LocalDateTime.now();

            // Summary stats
            lenient().when(eventRepository.countSecurityEventsByTimeRange(TENANT_ID, start, end)).thenReturn(50L);
            lenient().when(eventRepository.countByTenantIdAndSeverityAndTimeRange(TENANT_ID, "critical", start, end)).thenReturn(5L);
            lenient().when(eventRepository.countByTenantIdAndSeverityAndTimeRange(TENANT_ID, "high", start, end)).thenReturn(15L);
            lenient().when(eventRepository.countByTenantIdAndSeverityAndTimeRange(TENANT_ID, "medium", start, end)).thenReturn(20L);
            lenient().when(eventRepository.countByTenantIdAndSeverityAndTimeRange(TENANT_ID, "low", start, end)).thenReturn(10L);

            // Previous period count
            LocalDateTime prevStart = start.minusHours(java.time.Duration.between(start, end).toHours());
            lenient().when(eventRepository.countSecurityEventsByTimeRange(eq(TENANT_ID), eq(prevStart), eq(start)))
                    .thenReturn(40L);

            // Event type breakdown stubs
            lenient().when(eventRepository.countByTenantIdAndSeverityInAndTimeRange(
                    TENANT_ID, List.of("critical", "high"), start, end)).thenReturn(20L);
            lenient().when(eventRepository.countPolicyViolationsByTimeRange(TENANT_ID, start, end)).thenReturn(15L);
            lenient().when(eventRepository.countDlpAlertsByTimeRange(TENANT_ID, start, end)).thenReturn(10L);
            lenient().when(eventRepository.countComplianceEventsByTimeRange(TENANT_ID, start, end)).thenReturn(5L);

            // Paged events
            Page<Event> eventsPage = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findSecurityEventsByTimeRange(eq(TENANT_ID), eq(start), eq(end), any(Pageable.class)))
                    .thenReturn(eventsPage);

            // ACT
            SecurityDashboardDTO dashboard = service.getSecurityDashboard(
                    TENANT_ID, start, end, 0, 10, null, null);

            // ASSERT
            assertNotNull(dashboard);
            assertNotNull(dashboard.getSummary());
            assertEquals(50L, dashboard.getSummary().getTotalEvents());
            assertEquals(25.0, dashboard.getSummary().getTotalEventsChange(), 0.1);
            assertEquals(5L, dashboard.getSummary().getCriticalEvents());
            assertEquals(15L, dashboard.getSummary().getHighPriorityEvents());
            assertEquals(30L, dashboard.getSummary().getMediumLowEvents()); // 20+10

            // Breakdown
            assertEquals(4, dashboard.getEventsByType().size());
            assertEquals(20L, dashboard.getEventsByType().get(0).getCount()); // SECURITY_THREAT

            // Recent events
            assertEquals(1, dashboard.getRecentEvents().size());
            assertEquals("ev-1", dashboard.getRecentEvents().get(0).getEventId());

            // Pagination
            assertEquals(1, dashboard.getPagination().getTotalElements());
        }
    }

    // ==================== getEventDetail ====================
    @Nested
    @DisplayName("getEventDetail")
    class GetEventDetailTests {

        @Test
        @DisplayName("Happy Path – event found without incident")
        void happyPath_eventFound_noIncident() {
            when(eventRepository.findByPkEventIdAndTenant_TenantID("ev-1", TENANT_ID))
                    .thenReturn(Optional.of(sampleEvent));
            when(incidentEventRepository.findFirstByEvent_PkEventIdAndTenantIdOrderByLinkedAtDesc("ev-1", TENANT_ID))
                    .thenReturn(Optional.empty());

            SecurityDashboardDTO.EventDetailDTO detail = service.getEventDetail(TENANT_ID, "ev-1");
            assertNotNull(detail);
            assertEquals("ev-1", detail.getEventId());
            assertFalse(detail.getHasIncident());
        }

        @Test
        @DisplayName("Happy Path – event with linked incident")
        void happyPath_withIncident() {
            when(eventRepository.findByPkEventIdAndTenant_TenantID("ev-1", TENANT_ID))
                    .thenReturn(Optional.of(sampleEvent));

            Incident incident = new Incident();
            incident.setPkIncidentId("inc-1");
            incident.setIncidentNumber("INC-000001");
            incident.setStatus(IncidentStatus.OPEN);
            incident.setPriority(IncidentPriority.P1_CRITICAL);
            incident.setTitle("Critical incident");

            IncidentEvent ie = new IncidentEvent();
            ie.setIncident(incident);
            ie.setEvent(sampleEvent);

            when(incidentEventRepository.findFirstByEvent_PkEventIdAndTenantIdOrderByLinkedAtDesc("ev-1", TENANT_ID))
                    .thenReturn(Optional.of(ie));

            SecurityDashboardDTO.EventDetailDTO detail = service.getEventDetail(TENANT_ID, "ev-1");
            assertNotNull(detail);
            assertTrue(detail.getHasIncident());
            assertEquals("inc-1", detail.getIncidentId());
            assertEquals("INC-000001", detail.getIncidentNumber());
            assertEquals("OPEN", detail.getIncidentStatus());
            assertEquals("P1_CRITICAL", detail.getIncidentPriority());
        }

        @Test
        @DisplayName("Sad Path – event not found returns null")
        void sadPath_notFound() {
            when(eventRepository.findByPkEventIdAndTenant_TenantID("bad", TENANT_ID))
                    .thenReturn(Optional.empty());

            assertNull(service.getEventDetail(TENANT_ID, "bad"));
        }
    }

    // ==================== getFilterOptions ====================
    @Nested
    @DisplayName("getFilterOptions")
    class GetFilterOptionsTests {

        @Test
        @DisplayName("Happy Path – returns filter options with devices and users")
        void happyPath() {
            when(eventRepository.findDistinctDeviceNamesByTenantId(TENANT_ID)).thenReturn(List.of("Device1", "Device2"));
            when(eventRepository.findDistinctUserNamesByTenantId(TENANT_ID)).thenReturn(List.of("user1", "user2"));

            SecurityDashboardDTO.FilterOptions options = service.getFilterOptions(TENANT_ID);
            assertNotNull(options);
            assertEquals(4, options.getEventTypes().size());
            assertEquals(4, options.getSeverityLevels().size());
            assertEquals(4, options.getRiskLevels().size());
            assertEquals(3, options.getActionTypes().size());
            assertEquals(2, options.getDevices().size());
            assertEquals(2, options.getUsers().size());
        }
    }
}