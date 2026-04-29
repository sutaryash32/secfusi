package com.secufusion.events.controller;

import com.secufusion.events.dto.BrowserAnalyticsDTO;
import com.secufusion.events.dto.BrowserAnalyticsDTO.DailyTrendDTO;
import com.secufusion.events.dto.BrowserAnalyticsDTO.DomainAccessDTO;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.BrowserAnalyticsService;
import com.secufusion.events.util.JwtUtl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrowserAnalyticsController Tests")
class BrowserAnalyticsControllerTest {

    @Mock
    private BrowserAnalyticsService browserAnalyticsService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private BrowserAnalyticsController controller;

    private MockMvc mockMvc;

    private static final String TENANT_ID = "tenant-123";
    private static final String BASE_URL = "/api/events/analytics/browser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant testTenant = new Tenant();
        testTenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(testTenant);
    }

    private BrowserAnalyticsDTO buildAnalyticsDTO() {
        BrowserAnalyticsDTO dto = new BrowserAnalyticsDTO();
        dto.setTotalDownloads(100L);
        dto.setTotalUploads(50L);
        dto.setBlockedDownloads(10L);
        dto.setBlockedUploads(5L);
        dto.setDailyActivityTrends(Collections.emptyList());
        dto.setTopAccessedDomains(Collections.emptyList());
        return dto;
    }

    // ==================== GET / (getBrowserAnalytics) ====================
    @Nested
    @DisplayName("GET /api/events/analytics/browser")
    class GetBrowserAnalyticsTests {

        private static final String URL = BASE_URL;

        @Test
        @DisplayName("Happy Path – period parameter (valid 30_DAYS)")
        void happyPath_withPeriod() throws Exception {
            BrowserAnalyticsDTO analytics = buildAnalyticsDTO();
            when(browserAnalyticsService.getBrowserAnalytics(TENANT_ID, "30_DAYS"))
                    .thenReturn(analytics);

            mockMvc.perform(get(URL)
                            .param("period", "30_DAYS")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalDownloads", is(100)))   // payload under results
                    .andExpect(jsonPath("$.code", is("200")));                  // status code

            verify(browserAnalyticsService).getBrowserAnalytics(TENANT_ID, "30_DAYS");
        }

        @Test
        @DisplayName("Happy Path – custom date range")
        void happyPath_withDateRange() throws Exception {
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            BrowserAnalyticsDTO analytics = buildAnalyticsDTO();
            when(browserAnalyticsService.getBrowserAnalytics(TENANT_ID, start, end))
                    .thenReturn(analytics);

            mockMvc.perform(get(URL)
                            .param("startDate", "2025-01-01")
                            .param("endDate", "2025-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalDownloads", is(100)))
                    .andExpect(jsonPath("$.code", is("200")));

            verify(browserAnalyticsService).getBrowserAnalytics(TENANT_ID, start, end);
        }

        @Test
        @DisplayName("Happy Path – default period when none specified")
        void happyPath_defaultPeriod() throws Exception {
            when(browserAnalyticsService.getBrowserAnalytics(TENANT_ID, "30_DAYS"))
                    .thenReturn(buildAnalyticsDTO());

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));

            verify(browserAnalyticsService).getBrowserAnalytics(TENANT_ID, "30_DAYS");
        }

        @Test
        @DisplayName("Sad Path – invalid period string")
        void sadPath_invalidPeriod() throws Exception {
            mockMvc.perform(get(URL)
                            .param("period", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("Invalid period")));   // error in code field
        }

        @Test
        @DisplayName("Sad Path – startDate after endDate")
        void sadPath_startAfterEnd() throws Exception {
            mockMvc.perform(get(URL)
                            .param("startDate", "2025-02-01")
                            .param("endDate", "2025-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("cannot be after endDate")));
        }

        @Test
        @DisplayName("Sad Path – date range exceeds 1 year")
        void sadPath_rangeExceedsOneYear() throws Exception {
            mockMvc.perform(get(URL)
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2025-12-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("cannot exceed 1 year")));
        }

        @Test
        @DisplayName("Sad Path – service throws exception → 500")
        void sadPath_serviceException() throws Exception {
            when(browserAnalyticsService.getBrowserAnalytics(anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB down"));

            mockMvc.perform(get(URL)
                            .param("period", "7_DAYS"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB down")));            // exception message in code
        }
    }

    // ==================== GET /file-operations ====================
    @Nested
    @DisplayName("GET /file-operations")
    class GetFileOperationsStatsTests {

        private static final String URL = BASE_URL + "/file-operations";

        @Test
        @DisplayName("Happy Path – period")
        void happyPath_period() throws Exception {
            BrowserAnalyticsDTO analytics = buildAnalyticsDTO();
            when(browserAnalyticsService.getBrowserAnalytics(TENANT_ID, "30_DAYS"))
                    .thenReturn(analytics);

            mockMvc.perform(get(URL)
                            .param("period", "30_DAYS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalDownloads", is(100)))
                    .andExpect(jsonPath("$.results.totalUploads", is(50)))
                    .andExpect(jsonPath("$.results.blockedDownloads", is(10)))
                    .andExpect(jsonPath("$.results.blockedUploads", is(5)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception")
        void sadPath_serviceException() throws Exception {
            when(browserAnalyticsService.getBrowserAnalytics(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Service error"));

            mockMvc.perform(get(URL)
                            .param("period", "7_DAYS"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Service error")));
        }
    }

    // ==================== GET /trends ====================
    @Nested
    @DisplayName("GET /trends")
    class GetDailyTrendsTests {

        private static final String URL = BASE_URL + "/trends";

        @Test
        @DisplayName("Happy Path – returns daily trends list")
        void happyPath() throws Exception {
            BrowserAnalyticsDTO analytics = buildAnalyticsDTO();
            DailyTrendDTO trend = new DailyTrendDTO();
            trend.setDate("2025-01-15");
            trend.setEvents(10);
            analytics.setDailyActivityTrends(Arrays.asList(trend));
            when(browserAnalyticsService.getBrowserAnalytics(TENANT_ID, "30_DAYS")).thenReturn(analytics);

            mockMvc.perform(get(URL)
                            .param("period", "30_DAYS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].date", is("2025-01-15")))   // list directly in results
                    .andExpect(jsonPath("$.results[0].events", is(10)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception")
        void sadPath_serviceException() throws Exception {
            when(browserAnalyticsService.getBrowserAnalytics(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Trends failed"));

            mockMvc.perform(get(URL))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Trends failed")));
        }
    }

    // ==================== GET /domains ====================
    @Nested
    @DisplayName("GET /domains")
    class GetTopDomainsTests {

        private static final String URL = BASE_URL + "/domains";

        @Test
        @DisplayName("Happy Path – returns domain list")
        void happyPath() throws Exception {
            BrowserAnalyticsDTO analytics = buildAnalyticsDTO();
            DomainAccessDTO domain = new DomainAccessDTO();
            domain.setDomain("example.com");
            domain.setVisits(42);
            analytics.setTopAccessedDomains(Arrays.asList(domain));
            when(browserAnalyticsService.getBrowserAnalytics(TENANT_ID, "30_DAYS")).thenReturn(analytics);

            mockMvc.perform(get(URL)
                            .param("period", "30_DAYS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].domain", is("example.com")))
                    .andExpect(jsonPath("$.results[0].visits", is(42)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception")
        void sadPath_serviceException() throws Exception {
            when(browserAnalyticsService.getBrowserAnalytics(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Domains failed"));

            mockMvc.perform(get(URL))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Domains failed")));
        }
    }
}