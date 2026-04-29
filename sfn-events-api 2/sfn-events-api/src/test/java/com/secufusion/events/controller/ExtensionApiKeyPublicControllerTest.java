package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.service.ExtensionTokenService;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtensionApiKeyPublicController Tests")
class ExtensionApiKeyPublicControllerTest {

    @Mock
    private ExtensionTokenService tokenService;

    @InjectMocks
    private ExtensionApiKeyPublicController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_KEY = "sk-raw-key";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== POST /validate ====================
    @Nested
    @DisplayName("POST /api/events/public/extension/validate")
    class ValidateApiKeyTests {

        @Test
        @DisplayName("Happy Path – returns validation response")
        void happyPath() throws Exception {
            ValidateExtensionApiKeyRequest req = new ValidateExtensionApiKeyRequest();
            req.setApiKey(API_KEY);

            ValidateExtensionApiKeyResponse resp = new ValidateExtensionApiKeyResponse();
            resp.setValid(true);
            resp.setTenantId("tenant-1");
            when(tokenService.validateApiKey(API_KEY)).thenReturn(resp);

            mockMvc.perform(post("/api/events/public/extension/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)))
                    .andExpect(jsonPath("$.tenantId", is("tenant-1")));
        }
    }

    // ==================== POST /generate-token ====================
    @Nested
    @DisplayName("POST /api/events/public/extension/generate-token")
    class GenerateTokenTests {

        @Test
        @DisplayName("Happy Path – returns token")
        void happyPath() throws Exception {
            GenerateTokenRequest req = new GenerateTokenRequest();
            req.setApiKey(API_KEY);
            GenerateTokenRequest.DeviceUserDetails details = new GenerateTokenRequest.DeviceUserDetails();
            details.setEmail("user@example.com");
            details.setUserName("User");
            details.setDisplayName("Display");
            req.setDeviceUserDetails(details);

            GenerateTokenResponse resp = new GenerateTokenResponse();
            resp.setAccessToken("jwt-token");
            resp.setTokenType("Bearer");
            when(tokenService.generateToken(any(GenerateTokenRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/api/events/public/extension/generate-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("jwt-token")))
                    .andExpect(jsonPath("$.tokenType", is("Bearer")));
        }
    }

    // ==================== POST /generate-token-with-steps ====================
    @Nested
    @DisplayName("POST /api/events/public/extension/generate-token-with-steps")
    class GenerateTokenWithStepsTests {

        @Test
        @DisplayName("Happy Path – returns step-by-step response")
        void happyPath() throws Exception {
            GenerateTokenRequest req = new GenerateTokenRequest();
            req.setApiKey(API_KEY);
            GenerateTokenRequest.DeviceUserDetails details = new GenerateTokenRequest.DeviceUserDetails();
            details.setEmail("user@example.com");
            req.setDeviceUserDetails(details);

            TokenValidationStepResponse resp = new TokenValidationStepResponse();
            resp.setSuccess(true);
            when(tokenService.generateTokenWithSteps(any(GenerateTokenRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/api/events/public/extension/generate-token-with-steps")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }
    }

    // ==================== POST /check-expiry ====================
    @Nested
    @DisplayName("POST /api/events/public/extension/check-expiry")
    class CheckExpiryTests {

        @Test
        @DisplayName("Happy Path – returns expiry check")
        void happyPath() throws Exception {
            ValidateExtensionApiKeyRequest req = new ValidateExtensionApiKeyRequest();
            req.setApiKey(API_KEY);

            ApiKeyExpiryCheckResponse resp = new ApiKeyExpiryCheckResponse();
            resp.setValid(true);
            resp.setExpiringSoon(false);
            when(tokenService.checkExpiry(API_KEY)).thenReturn(resp);

            mockMvc.perform(post("/api/events/public/extension/check-expiry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)))
                    .andExpect(jsonPath("$.expiringSoon", is(false)));
        }
    }

    // ==================== POST /usage-stats ====================
    @Nested
    @DisplayName("POST /api/events/public/extension/usage-stats")
    class UsageStatsTests {

        @Test
        @DisplayName("Happy Path – returns usage stats")
        void happyPath() throws Exception {
            ValidateExtensionApiKeyRequest req = new ValidateExtensionApiKeyRequest();
            req.setApiKey(API_KEY);

            ApiKeyUsageStatsResponse resp = new ApiKeyUsageStatsResponse();
            resp.setTotalUsers(42);
            when(tokenService.getUsageStats(API_KEY)).thenReturn(resp);

            mockMvc.perform(post("/api/events/public/extension/usage-stats")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUsers", is(42)));
        }
    }

    // ==================== POST /refresh-token ====================
    @Nested
    @DisplayName("POST /api/events/public/extension/refresh-token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Happy Path – refreshes token (alias for generate-token)")
        void happyPath() throws Exception {
            GenerateTokenRequest req = new GenerateTokenRequest();
            req.setApiKey(API_KEY);
            GenerateTokenRequest.DeviceUserDetails details = new GenerateTokenRequest.DeviceUserDetails();
            details.setEmail("user@example.com");
            req.setDeviceUserDetails(details);

            GenerateTokenResponse resp = new GenerateTokenResponse();
            resp.setAccessToken("refreshed-jwt");
            when(tokenService.generateToken(any(GenerateTokenRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/api/events/public/extension/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("refreshed-jwt")));
        }
    }
}