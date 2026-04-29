package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.SecurityEventService;
import com.secufusion.events.util.JwtUtl;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityEventController Tests")
class SecurityEventControllerTest {

    @Mock
    private SecurityEventService securityEventService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private SecurityEventController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String EVENT_ID = "ev-123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
    }

    // ==================== GET / ====================
    @Nested
    @DisplayName("GET /api/events/security")
    class GetSecurityEventsTests {

        @Test
        @DisplayName("Happy Path – returns paginated security events")
        void happyPath() throws Exception {
            List<SecurityEventDTO> content = new ArrayList<>();
            content.add(SecurityEventDTO.builder().eventId(EVENT_ID).severity("high").build());
            Page<SecurityEventDTO> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(securityEventService.getSecurityEvents(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/security"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].eventId", is(EVENT_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(securityEventService.getSecurityEvents(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/events/security"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== GET /filter ====================
    @Nested
    @DisplayName("GET /api/events/security/filter")
    class GetSecurityEventsWithFiltersTests {

        @Test
        @DisplayName("Happy Path – returns filtered events")
        void happyPath() throws Exception {
            Page<SecurityEventDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(securityEventService.getSecurityEventsWithFilters(
                    eq(TENANT_ID), eq("high"), eq("malware"), eq("High"), eq("blocked"),
                    any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/events/security/filter")
                            .param("severity", "high")
                            .param("threatType", "malware")
                            .param("riskLevel", "High")
                            .param("actionTaken", "blocked")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(securityEventService.getSecurityEventsWithFilters(anyString(), any(), any(), any(), any(),
                    any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Filter error"));

            mockMvc.perform(get("/api/events/security/filter")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Filter error")));
        }
    }

    // ==================== GET /severity/{severity} ====================
    @Nested
    @DisplayName("GET /api/events/security/severity/{severity}")
    class GetSecurityEventsBySeverityTests {

        @Test
        @DisplayName("Happy Path – returns events by severity")
        void happyPath() throws Exception {
            Page<SecurityEventDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(securityEventService.getSecurityEventsBySeverity(TENANT_ID, "high", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/security/severity/high"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /threat-type/{threatType} ====================
    @Nested
    @DisplayName("GET /api/events/security/threat-type/{threatType}")
    class GetSecurityEventsByThreatTypeTests {

        @Test
        @DisplayName("Happy Path – returns events by threat type")
        void happyPath() throws Exception {
            Page<SecurityEventDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(securityEventService.getSecurityEventsByThreatType(TENANT_ID, "malware", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/security/threat-type/malware"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /risk-level/{riskLevel} ====================
    @Nested
    @DisplayName("GET /api/events/security/risk-level/{riskLevel}")
    class GetSecurityEventsByRiskLevelTests {

        @Test
        @DisplayName("Happy Path – returns events by risk level")
        void happyPath() throws Exception {
            Page<SecurityEventDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(securityEventService.getSecurityEventsByRiskLevel(TENANT_ID, "High", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/security/risk-level/High"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /user/{userName} ====================
    @Nested
    @DisplayName("GET /api/events/security/user/{userName}")
    class GetSecurityEventsByUserTests {

        @Test
        @DisplayName("Happy Path – returns events by user")
        void happyPath() throws Exception {
            Page<SecurityEventDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(securityEventService.getSecurityEventsByUser(TENANT_ID, "testuser", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/security/user/testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /device/{deviceId} ====================
    @Nested
    @DisplayName("GET /api/events/security/device/{deviceId}")
    class GetSecurityEventsByDeviceTests {

        @Test
        @DisplayName("Happy Path – returns events by device")
        void happyPath() throws Exception {
            Page<SecurityEventDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(securityEventService.getSecurityEventsByDevice("dev-1", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/security/device/dev-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /range ====================
    @Nested
    @DisplayName("GET /api/events/security/range")
    class GetSecurityEventsByTimeRangeTests {

        @Test
        @DisplayName("Happy Path – returns events in time range")
        void happyPath() throws Exception {
            List<SecurityEventDTO> events = List.of(SecurityEventDTO.builder().eventId(EVENT_ID).build());
            when(securityEventService.getSecurityEventsByTimeRange(eq(TENANT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(events);

            mockMvc.perform(get("/api/events/security/range")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].eventId", is(EVENT_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /critical ====================
    @Nested
    @DisplayName("GET /api/events/security/critical")
    class GetCriticalSecurityEventsTests {

        @Test
        @DisplayName("Happy Path – returns critical events")
        void happyPath() throws Exception {
            List<SecurityEventDTO> events = List.of(SecurityEventDTO.builder().eventId(EVENT_ID).severity("critical").build());
            when(securityEventService.getCriticalSecurityEvents(eq(TENANT_ID), any(), any()))
                    .thenReturn(events);

            mockMvc.perform(get("/api/events/security/critical")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].severity", is("critical")));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/security/stats")
    class GetSecurityEventStatsTests {

        @Test
        @DisplayName("Happy Path – returns stats")
        void happyPath() throws Exception {
            SecurityEventStatsDTO stats = new SecurityEventStatsDTO();
            stats.setTotalSecurityEvents(100L);
            when(securityEventService.getSecurityEventStats(eq(TENANT_ID), any(), any()))
                    .thenReturn(stats);

            mockMvc.perform(get("/api/events/security/stats")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalSecurityEvents", is(100)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /count ====================
    @Nested
    @DisplayName("GET /api/events/security/count")
    class GetSecurityEventCountTests {

        @Test
        @DisplayName("Happy Path – returns total count")
        void happyPath() throws Exception {
            when(securityEventService.countSecurityEvents(TENANT_ID)).thenReturn(42L);

            mockMvc.perform(get("/api/events/security/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results", is(42)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Happy Path – count with time range")
        void happyPath_withTimeRange() throws Exception {
            when(securityEventService.countSecurityEventsByTimeRange(eq(TENANT_ID), any(), any())).thenReturn(10L);

            mockMvc.perform(get("/api/events/security/count")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results", is(10)));
        }
    }

    // ==================== GET /dashboard ====================
    @Nested
    @DisplayName("GET /api/events/security/dashboard")
    class GetSecurityDashboardTests {

        @Test
        @DisplayName("Happy Path – returns dashboard data")
        void happyPath() throws Exception {
            SecurityDashboardDTO dashboard = SecurityDashboardDTO.builder()
                    .summary(SecurityDashboardDTO.SummaryStats.builder().totalEvents(50L).build())
                    .build();
            when(securityEventService.getSecurityDashboard(eq(TENANT_ID), any(), any(), eq(0), eq(10), isNull(), isNull()))
                    .thenReturn(dashboard);

            mockMvc.perform(get("/api/events/security/dashboard")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.summary.totalEvents", is(50)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – start after end returns 400")
        void sadPath_invalidDateRange() throws Exception {
            mockMvc.perform(get("/api/events/security/dashboard")
                            .param("start", "2025-01-02T00:00:00")
                            .param("end", "2025-01-01T00:00:00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("before 'end'")));
        }
    }

    // ==================== GET /detail/{eventId} ====================
    @Nested
    @DisplayName("GET /api/events/security/detail/{eventId}")
    class GetEventDetailTests {

        @Test
        @DisplayName("Happy Path – returns event detail")
        void happyPath() throws Exception {
            SecurityDashboardDTO.EventDetailDTO detail = new SecurityDashboardDTO.EventDetailDTO();
            detail.setEventId(EVENT_ID);
            when(securityEventService.getEventDetail(TENANT_ID, EVENT_ID)).thenReturn(detail);

            mockMvc.perform(get("/api/events/security/detail/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.eventId", is(EVENT_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – event not found returns 404")
        void sadPath_notFound() throws Exception {
            when(securityEventService.getEventDetail(anyString(), anyString())).thenReturn(null);

            mockMvc.perform(get("/api/events/security/detail/{eventId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Event not found")));
        }
    }

    // ==================== GET /filters ====================
    @Nested
    @DisplayName("GET /api/events/security/filters")
    class GetFilterOptionsTests {

        @Test
        @DisplayName("Happy Path – returns filter options")
        void happyPath() throws Exception {
            SecurityDashboardDTO.FilterOptions filters = new SecurityDashboardDTO.FilterOptions();
            filters.setEventTypes(List.of("SECURITY_THREAT"));
            when(securityEventService.getFilterOptions(TENANT_ID)).thenReturn(filters);

            mockMvc.perform(get("/api/events/security/filters"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.eventTypes[0]", is("SECURITY_THREAT")))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }
}