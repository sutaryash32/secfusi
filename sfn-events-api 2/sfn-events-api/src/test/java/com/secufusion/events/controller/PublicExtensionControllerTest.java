package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.service.ExtensionSyncService;
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

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicExtensionController Tests")
class PublicExtensionControllerTest {

    @Mock
    private ExtensionSyncService extensionSyncService;

    @InjectMocks
    private PublicExtensionController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEVICE_TOKEN = "device-token-123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== POST /register-device ====================
    @Nested
    @DisplayName("POST /api/public/extension/register-device")
    class RegisterAnonymousDeviceTests {

        @Test
        @DisplayName("Happy Path – returns 200 with device token")
        void happyPath() throws Exception {
            AnonymousDeviceRegistrationRequest req = new AnonymousDeviceRegistrationRequest();
            req.setTenantCode("TENANT-CODE");
            req.setDeviceFingerprint("fp-123");
            req.setUserAgent("Chrome");   // required by @NotBlank

            AnonymousDeviceResponse resp = AnonymousDeviceResponse.builder()
                    .success(true)
                    .deviceToken("new-token")
                    .build();
            when(extensionSyncService.registerAnonymousDevice(any(AnonymousDeviceRegistrationRequest.class)))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/public/extension/register-device")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.deviceToken", is("new-token")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 400 with error")
        void sadPath_exception() throws Exception {
            AnonymousDeviceRegistrationRequest req = new AnonymousDeviceRegistrationRequest();
            req.setTenantCode("BAD");
            req.setUserAgent("Chrome");

            when(extensionSyncService.registerAnonymousDevice(any()))
                    .thenThrow(new RuntimeException("Tenant not found"));

            mockMvc.perform(post("/api/public/extension/register-device")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Tenant not found")));
        }
    }

    // ==================== POST /sync ====================
    @Nested
    @DisplayName("POST /api/public/extension/sync")
    class SyncExtensionsAnonymousTests {

        @Test
        @DisplayName("Happy Path – syncs and returns 200")
        void happyPath() throws Exception {
            ExtensionSyncRequest req = new ExtensionSyncRequest();
            req.setDeviceToken(DEVICE_TOKEN);
            req.setBrowserType("Chrome");                // required by @NotBlank
            req.setExtensions(Collections.emptyList());   // required by @NotNull

            ExtensionSyncResponse resp = ExtensionSyncResponse.builder()
                    .success(true)
                    .message("Synced")
                    .build();
            when(extensionSyncService.syncExtensionsAnonymous(any(ExtensionSyncRequest.class)))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/public/extension/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Synced")));
        }

        @Test
        @DisplayName("Sad Path – missing deviceToken returns 400")
        void sadPath_missingDeviceToken() throws Exception {
            ExtensionSyncRequest req = new ExtensionSyncRequest();
            req.setDeviceToken(null);  // explicitly null
            req.setBrowserType("Chrome");
            req.setExtensions(Collections.emptyList());

            mockMvc.perform(post("/api/public/extension/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", containsString("deviceToken")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 400")
        void sadPath_exception() throws Exception {
            ExtensionSyncRequest req = new ExtensionSyncRequest();
            req.setDeviceToken(DEVICE_TOKEN);
            req.setBrowserType("Chrome");
            req.setExtensions(Collections.emptyList());

            when(extensionSyncService.syncExtensionsAnonymous(any()))
                    .thenThrow(new RuntimeException("Device not found"));

            mockMvc.perform(post("/api/public/extension/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Device not found")));
        }
    }

    // ==================== GET /health ====================
    @Nested
    @DisplayName("GET /api/public/extension/health")
    class HealthCheckTests {

        @Test
        @DisplayName("Happy Path – returns OK")
        void happyPath() throws Exception {
            // The health controller uses `new ResponseDto<>("OK", ...)`.
            // Due to constructor overloading, this resolves to `ResponseDto(String message, String code)`
            // rather than the generic `ResponseDto(T results, String code)`, so the message field is populated.
            mockMvc.perform(get("/api/public/extension/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("OK")))  // message field contains the string
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /heartbeat ====================
    @Nested
    @DisplayName("POST /api/public/extension/heartbeat")
    class HeartbeatTests {

        @Test
        @DisplayName("Happy Path – returns heartbeat response")
        void happyPath() throws Exception {
            PublicExtensionController.HeartbeatRequest req =
                    PublicExtensionController.HeartbeatRequest.builder()
                            .deviceToken(DEVICE_TOKEN)
                            .extensionVersion("1.0")
                            .build();

            ExtensionSyncService.HeartbeatResult result =
                    new ExtensionSyncService.HeartbeatResult(true, 300, "v1", false);
            when(extensionSyncService.processHeartbeat(DEVICE_TOKEN, "1.0")).thenReturn(result);

            mockMvc.perform(post("/api/public/extension/heartbeat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.success", is(true)))
                    .andExpect(jsonPath("$.results.syncIntervalSeconds", is(300)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – missing deviceToken returns 400")
        void sadPath_missingDeviceToken() throws Exception {
            PublicExtensionController.HeartbeatRequest req =
                    PublicExtensionController.HeartbeatRequest.builder()
                            .deviceToken(null)
                            .build();

            mockMvc.perform(post("/api/public/extension/heartbeat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.results.success", is(false)))
                    .andExpect(jsonPath("$.code", is("400")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 404")
        void sadPath_serviceException() throws Exception {
            PublicExtensionController.HeartbeatRequest req =
                    PublicExtensionController.HeartbeatRequest.builder()
                            .deviceToken(DEVICE_TOKEN)
                            .build();

            when(extensionSyncService.processHeartbeat(eq(DEVICE_TOKEN), any()))
                    .thenThrow(new RuntimeException("Device not found"));

            mockMvc.perform(post("/api/public/extension/heartbeat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.results.success", is(false)))
                    .andExpect(jsonPath("$.results.message", is("Device not found")))
                    .andExpect(jsonPath("$.code", is("404")));
        }
    }
}