package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.ExtensionSyncService;
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

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtensionController Tests")
class ExtensionControllerTest {

    @Mock
    private ExtensionSyncService extensionSyncService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private ExtensionController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-1";
    private static final String USER_NAME = "testuser";
    private static final String DEVICE_ID = "device-123";
    private static final String DEVICE_TOKEN = "token-abc";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
        lenient().when(jwtUtl.getUserId(any())).thenReturn(USER_ID);
        lenient().when(jwtUtl.getPreferredUsernameFromRequest(any())).thenReturn(USER_NAME);
    }

    // ==================== POST /sync ====================
    @Nested
    @DisplayName("POST /api/events/extensions/sync")
    class SyncExtensionsTests {

        @Test
        @DisplayName("Happy Path – syncs and returns response")
        void happyPath() throws Exception {
            ExtensionSyncRequest req = new ExtensionSyncRequest();
            req.setDeviceId(DEVICE_ID);
            req.setBrowserType("Chrome");               // required @NotBlank
            req.setExtensions(Collections.emptyList());  // required @NotNull

            ExtensionSyncResponse resp = ExtensionSyncResponse.builder()
                    .success(true)
                    .message("Synced")
                    .build();
            when(extensionSyncService.syncExtensionsAuthenticated(any(), eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), anyString()))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/events/extensions/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Synced")));
        }

        @Test
        @DisplayName("Sad Path – service throws returns 400 with failure message")
        void sadPath_exception() throws Exception {
            ExtensionSyncRequest req = new ExtensionSyncRequest();
            req.setDeviceId(DEVICE_ID);
            req.setBrowserType("Chrome");
            req.setExtensions(Collections.emptyList());

            when(extensionSyncService.syncExtensionsAuthenticated(any(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Sync failed"));

            mockMvc.perform(post("/api/events/extensions/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Sync failed")));
        }
    }

    // ==================== POST /link-device ====================
    @Nested
    @DisplayName("POST /api/events/extensions/link-device")
    class LinkAnonymousDeviceTests {

        @Test
        @DisplayName("Happy Path – links device and returns 200")
        void happyPath() throws Exception {
            DeviceResponse device = new DeviceResponse();
            device.setDeviceId(DEVICE_ID);
            when(extensionSyncService.linkAnonymousDeviceToUser(DEVICE_TOKEN, TENANT_ID, USER_ID, USER_NAME))
                    .thenReturn(device);

            mockMvc.perform(post("/api/events/extensions/link-device")
                            .param("deviceToken", DEVICE_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.deviceId", is(DEVICE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(extensionSyncService.linkAnonymousDeviceToUser(eq("bad-token"), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(post("/api/events/extensions/link-device")
                            .param("deviceToken", "bad-token"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== POST /acknowledge-warning ====================
    @Nested
    @DisplayName("POST /api/events/extensions/acknowledge-warning")
    class AcknowledgeWarningTests {

        @Test
        @DisplayName("Happy Path – records acknowledgment")
        void happyPath() throws Exception {
            ExtensionController.WarningAcknowledgmentRequest req =
                    ExtensionController.WarningAcknowledgmentRequest.builder()
                            .deviceId(DEVICE_ID)
                            .extensionId("ext-1")
                            .userAction("ACKNOWLEDGED")
                            .userReason("Safe")
                            .build();

            doNothing().when(extensionSyncService).acknowledgeWarning(
                    eq(TENANT_ID), eq(DEVICE_ID), eq("ext-1"),
                    eq(USER_ID), eq(USER_NAME), eq("ACKNOWLEDGED"), eq("Safe"));

            mockMvc.perform(post("/api/events/extensions/acknowledge-warning")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service error returns 404")
        void sadPath_error() throws Exception {
            ExtensionController.WarningAcknowledgmentRequest req =
                    ExtensionController.WarningAcknowledgmentRequest.builder()
                            .deviceId(DEVICE_ID)
                            .extensionId("ext-1")
                            .userAction("ACKNOWLEDGED")
                            .build();

            doThrow(new RuntimeException("Extension not found")).when(extensionSyncService)
                    .acknowledgeWarning(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());

            mockMvc.perform(post("/api/events/extensions/acknowledge-warning")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Extension not found")));
        }
    }

    // ==================== GET /search ====================
    @Nested
    @DisplayName("GET /api/events/extensions/search")
    class SearchExtensionsTests {

        @Test
        @DisplayName("Happy Path – returns search results")
        void happyPath() throws Exception {
            List<InstalledExtensionDto> content = new ArrayList<>();
            Page<InstalledExtensionDto> page = new PageImpl<>(content, PageRequest.of(0, 20), 0);
            when(extensionSyncService.searchExtensions(TENANT_ID, "test", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extensions/search")
                            .param("q", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – missing q returns 400")
        void sadPath_missingTerm() throws Exception {
            mockMvc.perform(get("/api/events/extensions/search")
                            .param("q", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("required")));
        }
    }

    // ==================== GET / ====================
    @Nested
    @DisplayName("GET /api/events/extensions")
    class GetAllExtensionsTests {

        @Test
        @DisplayName("Happy Path – returns paginated extensions")
        void happyPath() throws Exception {
            List<InstalledExtensionDto> content = new ArrayList<>();
            Page<InstalledExtensionDto> page = new PageImpl<>(content, PageRequest.of(0, 20), 0);
            when(extensionSyncService.getAllExtensions(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extensions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /device/{deviceId} ====================
    @Nested
    @DisplayName("GET /api/events/extensions/device/{deviceId}")
    class GetDeviceExtensionsTests {

        @Test
        @DisplayName("Happy Path – returns device extensions")
        void happyPath() throws Exception {
            when(extensionSyncService.getDeviceExtensions(DEVICE_ID))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/events/extensions/device/{deviceId}", DEVICE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(extensionSyncService.getDeviceExtensions("bad"))
                    .thenThrow(new RuntimeException("Device not found"));

            mockMvc.perform(get("/api/events/extensions/device/{deviceId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Device not found")));
        }
    }

    // ==================== GET /high-risk ====================
    @Nested
    @DisplayName("GET /api/events/extensions/high-risk")
    class GetHighRiskExtensionsTests {

        @Test
        @DisplayName("Happy Path – returns high-risk extensions")
        void happyPath() throws Exception {
            when(extensionSyncService.getHighRiskExtensions(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/events/extensions/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /blocked ====================
    @Nested
    @DisplayName("GET /api/events/extensions/blocked")
    class GetBlockedExtensionsTests {

        @Test
        @DisplayName("Happy Path – returns blocked extensions")
        void happyPath() throws Exception {
            when(extensionSyncService.getBlockedExtensions(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/events/extensions/blocked"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/extensions/stats")
    class GetExtensionStatsTests {

        @Test
        @DisplayName("Happy Path – returns stats map")
        void happyPath() throws Exception {
            when(extensionSyncService.getExtensionStats(TENANT_ID))
                    .thenReturn(Map.of("total", 10L));

            mockMvc.perform(get("/api/events/extensions/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.total", is(10)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /events ====================
    @Nested
    @DisplayName("GET /api/events/extensions/events")
    class GetExtensionEventsTests {

        @Test
        @DisplayName("Happy Path – returns paginated events")
        void happyPath() throws Exception {
            List<ExtensionEventDto> content = new ArrayList<>();
            Page<ExtensionEventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), 0);
            when(extensionSyncService.getExtensionEvents(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extensions/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /events/device/{deviceId} ====================
    @Nested
    @DisplayName("GET /api/events/extensions/events/device/{deviceId}")
    class GetDeviceExtensionEventsTests {

        @Test
        @DisplayName("Happy Path – returns device events")
        void happyPath() throws Exception {
            List<ExtensionEventDto> content = new ArrayList<>();
            Page<ExtensionEventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), 0);
            when(extensionSyncService.getDeviceExtensionEvents(DEVICE_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extensions/events/device/{deviceId}", DEVICE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(extensionSyncService.getDeviceExtensionEvents("bad", 0, 20))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/extensions/events/device/{deviceId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }
}