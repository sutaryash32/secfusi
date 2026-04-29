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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActivityController Tests")
class UserActivityControllerTest {

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private UserActivityController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
    }

    // ==================== GET / ====================
    @Nested
    @DisplayName("GET /api/events/user-activity")
    class GetUserListTests {

        @Test
        @DisplayName("Happy Path – returns paginated user list")
        void happyPath() throws Exception {
            List<UserListDto> content = new ArrayList<>();
            content.add(UserListDto.builder().userId(USER_ID).userName("testuser").build());
            Page<UserListDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(userActivityService.getUserList(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/user-activity"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].userId", is(USER_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.getUserList(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/events/user-activity"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== GET /search ====================
    @Nested
    @DisplayName("GET /api/events/user-activity/search")
    class SearchUsersTests {

        @Test
        @DisplayName("Happy Path – returns search results")
        void happyPath() throws Exception {
            Page<UserListDto> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(userActivityService.searchUsers(TENANT_ID, "test", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/user-activity/search")
                            .param("q", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – missing q returns 400")
        void sadPath_missingTerm() throws Exception {
            mockMvc.perform(get("/api/events/user-activity/search")
                            .param("q", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("required")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.searchUsers(anyString(), anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Search error"));

            mockMvc.perform(get("/api/events/user-activity/search")
                            .param("q", "test"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Search error")));
        }
    }

    // ==================== GET /{userId} ====================
    @Nested
    @DisplayName("GET /api/events/user-activity/{userId}")
    class GetUserDetailsTests {

        @Test
        @DisplayName("Happy Path – returns user details with devices")
        void happyPath() throws Exception {
            UserWithDevicesDto dto = new UserWithDevicesDto();
            dto.setUserId(USER_ID);                  // correct field name
            dto.setUserName("testuser");
            when(userActivityService.getUserWithDevices(TENANT_ID, USER_ID)).thenReturn(dto);

            mockMvc.perform(get("/api/events/user-activity/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.userId", is(USER_ID)))  // correct JSON property
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getUserWithDevices(anyString(), anyString()))
                    .thenThrow(new RuntimeException("User not found"));

            mockMvc.perform(get("/api/events/user-activity/{userId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("User not found")));
        }
    }

    // ==================== GET /{userId}/devices ====================
    @Nested
    @DisplayName("GET /api/events/user-activity/{userId}/devices")
    class GetUserDevicesTests {

        @Test
        @DisplayName("Happy Path – returns list of devices")
        void happyPath() throws Exception {
            List<DeviceResponse> devices = List.of(new DeviceResponse());
            when(userActivityService.getUserDevices(TENANT_ID, USER_ID)).thenReturn(devices);

            mockMvc.perform(get("/api/events/user-activity/{userId}/devices", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getUserDevices(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/user-activity/{userId}/devices", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== GET /{userId}/extensions ====================
    @Nested
    @DisplayName("GET /api/events/user-activity/{userId}/extensions")
    class GetUserExtensionsTests {

        @Test
        @DisplayName("Happy Path – returns list of extensions")
        void happyPath() throws Exception {
            List<InstalledExtensionDto> extensions = List.of(new InstalledExtensionDto());
            when(userActivityService.getUserExtensions(TENANT_ID, USER_ID)).thenReturn(extensions);

            mockMvc.perform(get("/api/events/user-activity/{userId}/extensions", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getUserExtensions(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/user-activity/{userId}/extensions", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== GET /{userId}/extension-events ====================
    @Nested
    @DisplayName("GET /api/events/user-activity/{userId}/extension-events")
    class GetUserExtensionEventsTests {

        @Test
        @DisplayName("Happy Path – returns paginated events")
        void happyPath() throws Exception {
            List<ExtensionEventDto> content = new ArrayList<>();
            Page<ExtensionEventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), 0);
            when(userActivityService.getUserExtensionEvents(TENANT_ID, USER_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/user-activity/{userId}/extension-events", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – not found returns 404")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getUserExtensionEvents(anyString(), anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/user-activity/{userId}/extension-events", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/user-activity/stats")
    class GetUserStatsTests {

        @Test
        @DisplayName("Happy Path – returns stats map")
        void happyPath() throws Exception {
            Map<String, Object> stats = Map.of("totalUsers", 50L);
            when(userActivityService.getUserStats(TENANT_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/events/user-activity/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalUsers", is(50)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(userActivityService.getUserStats(anyString()))
                    .thenThrow(new RuntimeException("Stats error"));

            mockMvc.perform(get("/api/events/user-activity/stats"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Stats error")));
        }
    }
}