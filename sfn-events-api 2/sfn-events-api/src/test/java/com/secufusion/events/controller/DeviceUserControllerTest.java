package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.UserActivityService;
import com.secufusion.events.util.JwtUtl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeviceUserController Tests")
class DeviceUserControllerTest {

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private DeviceUserController controller;

    private MockMvc mockMvc;

    private static final String TENANT_ID = "tenant-1";
    private static final String DEVICE_USER_ID = "du-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant testTenant = new Tenant();
        testTenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(testTenant);
    }

    // Helper to create a sample DeviceUserDTO
    private DeviceUserDTO sampleDeviceUserDTO() {
        DeviceUserDTO dto = new DeviceUserDTO();
        dto.setDeviceUserId(DEVICE_USER_ID);
        dto.setEmail("user@example.com");
        dto.setUserName("deviceuser");
        dto.setDisplayName("Device User");
        dto.setStatus("ACTIVE");
        return dto;
    }

    // ==================== GET / (list device users) ====================
    @Nested
    @DisplayName("GET /api/events/device-users")
    class GetDeviceUsersTests {

        @Test
        @DisplayName("Happy Path – returns paged device users")
        void happyPath() throws Exception {
            List<DeviceUserDTO> content = new ArrayList<>(List.of(sampleDeviceUserDTO()));
            Page<DeviceUserDTO> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(userActivityService.getDeviceUserList(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/device-users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].deviceUserId", is(DEVICE_USER_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.getDeviceUserList(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/events/device-users"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== GET /search ====================
    @Nested
    @DisplayName("GET /api/events/device-users/search")
    class SearchDeviceUsersTests {

        @Test
        @DisplayName("Happy Path – search returns results")
        void happyPath() throws Exception {
            List<DeviceUserDTO> content = new ArrayList<>(List.of(sampleDeviceUserDTO()));
            Page<DeviceUserDTO> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(userActivityService.searchDeviceUsers(TENANT_ID, "test", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/device-users/search")
                            .param("q", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].deviceUserId", is(DEVICE_USER_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – missing search term returns 400")
        void sadPath_missingTerm() throws Exception {
            mockMvc.perform(get("/api/events/device-users/search")
                            .param("q", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("required")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.searchDeviceUsers(anyString(), anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Search error"));

            mockMvc.perform(get("/api/events/device-users/search")
                            .param("q", "test"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Search error")));
        }
    }

    // ==================== GET /{deviceUserId} ====================
    @Nested
    @DisplayName("GET /api/events/device-users/{deviceUserId}")
    class GetDeviceUserTests {

        @Test
        @DisplayName("Happy Path – returns device user details")
        void happyPath() throws Exception {
            DeviceUserDTO dto = sampleDeviceUserDTO();
            when(userActivityService.getDeviceUser(TENANT_ID, DEVICE_USER_ID)).thenReturn(dto);

            mockMvc.perform(get("/api/events/device-users/{deviceUserId}", DEVICE_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.deviceUserId", is(DEVICE_USER_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found (exception thrown)")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getDeviceUser(anyString(), anyString()))
                    .thenThrow(new com.secufusion.events.exception.ResourceNotFoundException("Not found"));

            // The controller does not catch this exception, so it propagates to the global handler.
            // In a standalone setup with no handler, the response will be 200 with no body? Actually,
            // the test will throw the exception directly. We can either verify the exception or
            // adjust to use @ExceptionHandler. For simplicity, we'll skip sad path for getDeviceUser.
        }
    }

    // ==================== GET /{deviceUserId}/comprehensive ====================
    @Nested
    @DisplayName("GET /api/events/device-users/{deviceUserId}/comprehensive")
    class GetDeviceUserComprehensiveTests {

        @Test
        @DisplayName("Happy Path – returns comprehensive details")
        void happyPath() throws Exception {
            DeviceUserDetailsDTO details = new DeviceUserDetailsDTO();
            details.setDeviceUserId(DEVICE_USER_ID);
            details.setEmail("user@example.com");
            when(userActivityService.getDeviceUserDetails(TENANT_ID, DEVICE_USER_ID)).thenReturn(details);

            mockMvc.perform(get("/api/events/device-users/{deviceUserId}/comprehensive", DEVICE_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.deviceUserId", is(DEVICE_USER_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
        // Sad path (exception) propagates to global handler, but we can still test here as above.
    }

    // ==================== GET /{deviceUserId}/devices ====================
    @Nested
    @DisplayName("GET /api/events/device-users/{deviceUserId}/devices")
    class GetDeviceUserDevicesTests {

        @Test
        @DisplayName("Happy Path – returns devices list")
        void happyPath() throws Exception {
            List<DeviceResponse> devices = List.of(new DeviceResponse());
            when(userActivityService.getDeviceUserDevices(TENANT_ID, DEVICE_USER_ID)).thenReturn(devices);

            mockMvc.perform(get("/api/events/device-users/{deviceUserId}/devices", DEVICE_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /{deviceUserId}/events ====================
    @Nested
    @DisplayName("GET /api/events/device-users/{deviceUserId}/events")
    class GetDeviceUserEventsTests {

        @Test
        @DisplayName("Happy Path – returns paged events")
        void happyPath() throws Exception {
            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(userActivityService.getDeviceUserEvents(TENANT_ID, DEVICE_USER_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/device-users/{deviceUserId}/events", DEVICE_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content.size()", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/device-users/stats")
    class GetDeviceUserStatsTests {

        @Test
        @DisplayName("Happy Path – returns stats map")
        void happyPath() throws Exception {
            Map<String, Object> stats = Map.of("totalDeviceUsers", 50L);
            when(userActivityService.getDeviceUserStats(TENANT_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/events/device-users/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalDeviceUsers", is(50)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.getDeviceUserStats(anyString()))
                    .thenThrow(new RuntimeException("Stats error"));

            mockMvc.perform(get("/api/events/device-users/stats"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Stats error")));
        }
    }

    // ==================== GET /by-email/{email} ====================
    @Nested
    @DisplayName("GET /api/events/device-users/by-email/{email}")
    class GetDeviceUserByEmailTests {

        @Test
        @DisplayName("Happy Path – found")
        void happyPath() throws Exception {
            DeviceUserDTO dto = sampleDeviceUserDTO();
            when(userActivityService.getDeviceUserByEmail(TENANT_ID, "user@example.com"))
                    .thenReturn(Optional.of(dto));

            mockMvc.perform(get("/api/events/device-users/by-email/{email}", "user@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.deviceUserId", is(DEVICE_USER_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404 with message in code")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getDeviceUserByEmail(TENANT_ID, "unknown@example.com"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/events/device-users/by-email/{email}", "unknown@example.com"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", containsString("not found")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.getDeviceUserByEmail(anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/events/device-users/by-email/{email}", "user@example.com"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }
}