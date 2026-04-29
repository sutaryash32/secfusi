package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.ExtensionDashboardDTO;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.ExtensionDashboardService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtensionDashboardController Tests")
class ExtensionDashboardControllerTest {

    @Mock
    private ExtensionDashboardService dashboardService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private ExtensionDashboardController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String EXTENSION_ID = "ext-abc";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
    }

    // ==================== GET /overview ====================
    @Nested
    @DisplayName("GET /api/events/extensions/dashboard/overview")
    class GetOverviewTests {

        @Test
        @DisplayName("Happy Path – returns overview DTO")
        void happyPath() throws Exception {
            ExtensionDashboardDTO.Overview overview = new ExtensionDashboardDTO.Overview();
            overview.setTotalUniqueExtensions(10L);
            overview.setActiveInstallations(5L);
            when(dashboardService.getOverview(TENANT_ID)).thenReturn(overview);

            mockMvc.perform(get("/api/events/extensions/dashboard/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalUniqueExtensions", is(10)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /inventory ====================
    @Nested
    @DisplayName("GET /api/events/extensions/dashboard/inventory")
    class GetInventoryTests {

        @Test
        @DisplayName("Happy Path – returns paginated inventory")
        void happyPath() throws Exception {
            ExtensionDashboardDTO.InventoryItem item = new ExtensionDashboardDTO.InventoryItem();
            item.setExtensionId(EXTENSION_ID);
            item.setExtensionName("Test Ext");
            Page<ExtensionDashboardDTO.InventoryItem> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
            when(dashboardService.getInventory(TENANT_ID, null, null, null, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extensions/dashboard/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].extensionId", is(EXTENSION_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /inventory/{extensionId} ====================
    @Nested
    @DisplayName("GET /api/events/extensions/dashboard/inventory/{extensionId}")
    class GetExtensionDetailTests {

        @Test
        @DisplayName("Happy Path – returns extension detail")
        void happyPath() throws Exception {
            ExtensionDashboardDTO.ExtensionDetail detail = new ExtensionDashboardDTO.ExtensionDetail();
            detail.setExtensionId(EXTENSION_ID);
            detail.setExtensionName("Test Ext");
            when(dashboardService.getExtensionDetail(TENANT_ID, EXTENSION_ID)).thenReturn(detail);

            mockMvc.perform(get("/api/events/extensions/dashboard/inventory/{extensionId}", EXTENSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.extensionId", is(EXTENSION_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /trends ====================
    @Nested
    @DisplayName("GET /api/events/extensions/dashboard/trends")
    class GetTrendsTests {

        @Test
        @DisplayName("Happy Path – returns trends object")
        void happyPath() throws Exception {
            ExtensionDashboardDTO.Trends trends = new ExtensionDashboardDTO.Trends();
            trends.setDailyActivity(Collections.emptyList());
            when(dashboardService.getTrends(TENANT_ID, 30)).thenReturn(trends);

            mockMvc.perform(get("/api/events/extensions/dashboard/trends")
                            .param("days", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /user-risk ====================
    @Nested
    @DisplayName("GET /api/events/extensions/dashboard/user-risk")
    class GetUserRiskProfilesTests {

        @Test
        @DisplayName("Happy Path – returns paginated user risk profiles")
        void happyPath() throws Exception {
            Page<ExtensionDashboardDTO.UserRiskProfile> page =
                    new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(dashboardService.getUserRiskProfiles(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extensions/dashboard/user-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /policy-effectiveness ====================
    @Nested
    @DisplayName("GET /api/events/extensions/dashboard/policy-effectiveness")
    class GetPolicyEffectivenessTests {

        @Test
        @DisplayName("Happy Path – returns policy effectiveness")
        void happyPath() throws Exception {
            ExtensionDashboardDTO.PolicyEffectiveness pe = new ExtensionDashboardDTO.PolicyEffectiveness();
            pe.setBlockedCount(5L);
            when(dashboardService.getPolicyEffectiveness(TENANT_ID, 30)).thenReturn(pe);

            mockMvc.perform(get("/api/events/extensions/dashboard/policy-effectiveness")
                            .param("days", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.blockedCount", is(5)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /bulk-action ====================
    @Nested
    @DisplayName("POST /api/events/extensions/dashboard/bulk-action")
    class ExecuteBulkActionTests {

        @Test
        @DisplayName("Happy Path – executes bulk action")
        void happyPath() throws Exception {
            ExtensionDashboardDTO.BulkActionRequest req = new ExtensionDashboardDTO.BulkActionRequest();
            req.setExtensionIds(List.of(EXTENSION_ID));
            req.setAction("BLOCK");

            ExtensionDashboardDTO.BulkActionResult result = new ExtensionDashboardDTO.BulkActionResult();
            result.setUpdatedCount(1);
            result.setTotalRequested(1);
            result.setAction("BLOCK");
            when(dashboardService.executeBulkAction(eq(TENANT_ID), any(ExtensionDashboardDTO.BulkActionRequest.class)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/events/extensions/dashboard/bulk-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.updatedCount", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – missing extensionIds returns 400")
        void sadPath_missingIds() throws Exception {
            ExtensionDashboardDTO.BulkActionRequest req = new ExtensionDashboardDTO.BulkActionRequest();
            req.setExtensionIds(Collections.emptyList());
            req.setAction("BLOCK");

            mockMvc.perform(post("/api/events/extensions/dashboard/bulk-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("400")))
                    .andExpect(jsonPath("$.message", containsString("extensionIds")));
        }

        @Test
        @DisplayName("Sad Path – missing action returns 400")
        void sadPath_missingAction() throws Exception {
            ExtensionDashboardDTO.BulkActionRequest req = new ExtensionDashboardDTO.BulkActionRequest();
            req.setExtensionIds(List.of(EXTENSION_ID));
            req.setAction(null);

            mockMvc.perform(post("/api/events/extensions/dashboard/bulk-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("400")))
                    .andExpect(jsonPath("$.message", containsString("action")));
        }
    }
}