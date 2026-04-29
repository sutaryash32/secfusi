package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.DeviceStatus;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.ActivitySummaryService;
import com.secufusion.events.service.DeviceService;
import com.secufusion.events.service.EventService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceController Tests")
class DeviceControllerTest {

    @Mock private DeviceService deviceService;
    @Mock private ActivitySummaryService activitySummaryService;
    @Mock private EventService eventService;
    @Mock private UserActivityService userActivityService;
    @Mock private JwtUtl jwtUtl;

    @InjectMocks
    private DeviceController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "user-1";
    private static final String USER_NAME = "testuser";
    private static final String EMAIL = "user@test.com";
    private static final String DISPLAY_NAME = "Test User";
    private static final String DEVICE_ID = "dev-1";
    private static final String DEVICE_USER_ID = "du-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant testTenant = new Tenant();
        testTenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(testTenant);
        lenient().when(jwtUtl.getUserId(any())).thenReturn(USER_ID);
        lenient().when(jwtUtl.getPreferredUsernameFromRequest(any())).thenReturn(USER_NAME);
        lenient().when(jwtUtl.getEmail(any())).thenReturn(EMAIL);
        lenient().when(jwtUtl.getDisplayName(any())).thenReturn(DISPLAY_NAME);
    }

    private DeviceResponse sampleDeviceResponse() {
        DeviceResponse d = new DeviceResponse();
        d.setDeviceId(DEVICE_ID);
        d.setDeviceName("Test Device");
        d.setStatus(DeviceStatus.ACTIVE);
        d.setTenantId(TENANT_ID);
        return d;
    }

    // ==================== POST /register ====================
    @Nested
    @DisplayName("POST /api/events/devices/register")
    class RegisterDeviceTests {

        @Test
        @DisplayName("Happy Path - device registered successfully")
        void happyPath() throws Exception {
            DeviceRegistrationRequest req = new DeviceRegistrationRequest();
            req.setDeviceFingerprint("fp-1");
            req.setDeviceName("My Device");
            req.setUserAgent("Mozilla/5.0");

            when(deviceService.registerDevice(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME),
                    eq(EMAIL), eq(DISPLAY_NAME), any(), any()))
                    .thenReturn(sampleDeviceResponse());

            mockMvc.perform(post("/api/events/devices/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.deviceId", is(DEVICE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - service exception")
        void sadPath_exception() throws Exception {
            DeviceRegistrationRequest req = new DeviceRegistrationRequest();
            req.setDeviceFingerprint("fp-1");
            req.setUserAgent("Mozilla/5.0");

            when(deviceService.registerDevice(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Registration failed"));

            mockMvc.perform(post("/api/events/devices/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Registration failed")));
        }
    }

    // ==================== GET / (list devices) ====================
    @Nested
    @DisplayName("GET /api/events/devices")
    class GetDevicesTests {

        @Test
        @DisplayName("Happy Path - without status filter")
        void happyPath_noFilter() throws Exception {
            // Use mutable ArrayList directly
            List<DeviceResponse> content = new ArrayList<>(List.of(sampleDeviceResponse()));
            Page<DeviceResponse> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(deviceService.getDevices(TENANT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/devices"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].deviceId", is(DEVICE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Happy Path - with status filter")
        void happyPath_withStatus() throws Exception {
            List<DeviceResponse> content = new ArrayList<>(List.of(sampleDeviceResponse()));
            Page<DeviceResponse> page = new PageImpl<>(content, PageRequest.of(0, 10), content.size());
            when(deviceService.getDevicesByStatus(TENANT_ID, DeviceStatus.ACTIVE, 0, 10)).thenReturn(page);

            mockMvc.perform(get("/api/events/devices")
                            .param("status", "ACTIVE")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Sad Path - service exception")
        void sadPath_exception() throws Exception {
            when(deviceService.getDevices(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/events/devices"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== GET /{deviceId} ====================
    @Nested
    @DisplayName("GET /api/events/devices/{deviceId}")
    class GetDeviceTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            when(deviceService.getDevice(TENANT_ID, DEVICE_ID)).thenReturn(sampleDeviceResponse());

            mockMvc.perform(get("/api/events/devices/{deviceId}", DEVICE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.deviceId", is(DEVICE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - not found")
        void sadPath_notFound() throws Exception {
            when(deviceService.getDevice(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Device not found"));

            mockMvc.perform(get("/api/events/devices/{deviceId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Device not found")));
        }
    }

    // ==================== PUT /{deviceId}/status ====================
    @Nested
    @DisplayName("PUT /api/events/devices/{deviceId}/status")
    class UpdateDeviceStatusTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            DeviceStatusUpdateRequest req = new DeviceStatusUpdateRequest();
            req.setStatus(DeviceStatus.ACTIVE);
            when(deviceService.updateDeviceStatus(eq(TENANT_ID), eq(DEVICE_ID), any()))
                    .thenReturn(sampleDeviceResponse());

            mockMvc.perform(put("/api/events/devices/{deviceId}/status", DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - device not found")
        void sadPath_notFound() throws Exception {
            DeviceStatusUpdateRequest req = new DeviceStatusUpdateRequest();
            req.setStatus(DeviceStatus.ACTIVE);
            when(deviceService.updateDeviceStatus(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Device not found"));

            mockMvc.perform(put("/api/events/devices/{deviceId}/status", "bad")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Device not found")));
        }
    }

    // ==================== GET /{deviceId}/summary ====================
    @Nested
    @DisplayName("GET /api/events/devices/{deviceId}/summary")
    class GetDeviceActivitySummaryTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            DeviceActivitySummaryDTO summary = new DeviceActivitySummaryDTO();
            summary.setTotalEvents(10);
            when(activitySummaryService.getDeviceActivitySummary(TENANT_ID, DEVICE_ID))
                    .thenReturn(summary);

            mockMvc.perform(get("/api/events/devices/{deviceId}/summary", DEVICE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalEvents", is(10)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - device not found")
        void sadPath_notFound() throws Exception {
            when(activitySummaryService.getDeviceActivitySummary(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Device not found"));

            mockMvc.perform(get("/api/events/devices/{deviceId}/summary", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Device not found")));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/devices/stats")
    class GetDeviceStatsTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            Map<String, Long> stats = Map.of("total", 10L, "active", 5L);
            when(deviceService.getDeviceStats(TENANT_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/events/devices/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.total", is(10)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - service exception")
        void sadPath_exception() throws Exception {
            when(deviceService.getDeviceStats(anyString()))
                    .thenThrow(new RuntimeException("Stats error"));

            mockMvc.perform(get("/api/events/devices/stats"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Stats error")));
        }
    }

    // ==================== GET /recent ====================
    @Nested
    @DisplayName("GET /api/events/devices/recent")
    class GetRecentDevicesTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            when(deviceService.getRecentDevices(TENANT_ID, 5))
                    .thenReturn(List.of(sampleDeviceResponse()));

            mockMvc.perform(get("/api/events/devices/recent")
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].deviceId", is(DEVICE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - exception")
        void sadPath_exception() throws Exception {
            when(deviceService.getRecentDevices(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Failed"));

            mockMvc.perform(get("/api/events/devices/recent"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Failed")));
        }
    }

    // ==================== GET /by-type ====================
    @Nested
    @DisplayName("GET /api/events/devices/by-type")
    class GetDevicesByTypeTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            Map<String, Long> byType = Map.of("Desktop", 5L);
            when(deviceService.getDevicesByType(TENANT_ID)).thenReturn(byType);

            mockMvc.perform(get("/api/events/devices/by-type"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.Desktop", is(5)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - exception")
        void sadPath_exception() throws Exception {
            when(deviceService.getDevicesByType(anyString()))
                    .thenThrow(new RuntimeException("Error"));

            mockMvc.perform(get("/api/events/devices/by-type"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Error")));
        }
    }

    // ==================== GET /user/{userName} ====================
    @Nested
    @DisplayName("GET /api/events/devices/user/{userName}")
    class GetDevicesForUserTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            when(deviceService.getDevicesForUser(TENANT_ID, USER_NAME))
                    .thenReturn(List.of(sampleDeviceResponse()));

            mockMvc.perform(get("/api/events/devices/user/{userName}", USER_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].deviceId", is(DEVICE_ID)));
        }

        @Test
        @DisplayName("Sad Path - exception")
        void sadPath_exception() throws Exception {
            when(deviceService.getDevicesForUser(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Fail"));

            mockMvc.perform(get("/api/events/devices/user/baduser"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Fail")));
        }
    }

    // ==================== GET /device-user/{deviceUserId} ====================
    @Nested
    @DisplayName("GET /api/events/devices/device-user/{deviceUserId}")
    class GetDevicesForDeviceUserTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            when(userActivityService.getDeviceUserDevices(TENANT_ID, DEVICE_USER_ID))
                    .thenReturn(List.of(sampleDeviceResponse()));

            mockMvc.perform(get("/api/events/devices/device-user/{deviceUserId}", DEVICE_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - not found")
        void sadPath_notFound() throws Exception {
            when(userActivityService.getDeviceUserDevices(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/devices/device-user/{deviceUserId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== DELETE /{deviceId} ====================
    @Nested
    @DisplayName("DELETE /api/events/devices/{deviceId}")
    class DeactivateDeviceTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() throws Exception {
            when(deviceService.deactivateDevice(TENANT_ID, DEVICE_ID))
                    .thenReturn(sampleDeviceResponse());

            mockMvc.perform(delete("/api/events/devices/{deviceId}", DEVICE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path - not found")
        void sadPath_notFound() throws Exception {
            when(deviceService.deactivateDevice(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(delete("/api/events/devices/{deviceId}", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== GET /search ====================
    @Nested
    @DisplayName("GET /api/events/devices/search")
    class SearchDevicesTests {

        @Test
        @DisplayName("Happy Path - with search term")
        void happyPath_withTerm() throws Exception {
            List<DeviceResponse> content = new ArrayList<>(List.of(sampleDeviceResponse()));
            Page<DeviceResponse> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(deviceService.searchDevices(TENANT_ID, "test", null, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/devices/search")
                            .param("q", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].deviceId", is(DEVICE_ID)));
        }

        @Test
        @DisplayName("Sad Path - missing search term")
        void sadPath_missingTerm() throws Exception {
            mockMvc.perform(get("/api/events/devices/search")
                            .param("q", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", containsString("required")));
        }

        @Test
        @DisplayName("Sad Path - service exception")
        void sadPath_exception() throws Exception {
            when(deviceService.searchDevices(anyString(), anyString(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Search error"));

            mockMvc.perform(get("/api/events/devices/search")
                            .param("q", "test"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Search error")));
        }
    }

    // ==================== GET /{deviceId}/events ====================
    @Nested
    @DisplayName("GET /api/events/devices/{deviceId}/events")
    class GetDeviceEventsHistoryTests {

        @Test
        @DisplayName("Happy Path - paginated events")
        void happyPath_paginated() throws Exception {
            when(deviceService.getDevice(TENANT_ID, DEVICE_ID)).thenReturn(sampleDeviceResponse());

            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> eventsPage = new PageImpl<>(content, PageRequest.of(0, 50), content.size());
            when(eventService.getEventsByDevice(DEVICE_ID, 0, 50)).thenReturn(eventsPage);

            mockMvc.perform(get("/api/events/devices/{deviceId}/events", DEVICE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Happy Path - with date range")
        void happyPath_withDateRange() throws Exception {
            when(deviceService.getDevice(TENANT_ID, DEVICE_ID)).thenReturn(sampleDeviceResponse());
            List<EventDto> events = List.of(new EventDto());
            when(eventService.getEventsByDeviceAndTimeRange(anyString(), any(), any())).thenReturn(events);

            mockMvc.perform(get("/api/events/devices/{deviceId}/events", DEVICE_ID)
                            .param("startDate", "2025-01-01")
                            .param("endDate", "2025-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(1)));
        }

        @Test
        @DisplayName("Sad Path - device not found")
        void sadPath_deviceNotFound() throws Exception {
            when(deviceService.getDevice(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/devices/{deviceId}/events", "bad"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }
}