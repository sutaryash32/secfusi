package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.service.ExtensionApiKeyConfigurationService;
import com.secufusion.events.service.ExtensionApiKeyService;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtensionApiKeyManagementController Tests")
class ExtensionApiKeyManagementControllerTest {

    @Mock private ExtensionApiKeyService apiKeyService;
    @Mock private ExtensionApiKeyConfigurationService configService;
    @Mock private JwtUtl jwtUtl;

    @InjectMocks
    private ExtensionApiKeyManagementController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_EMAIL = "admin@example.com";
    private static final String KEY_ID = "key-123";

    @BeforeEach
    void setUp() {
        // Register JavaTimeModule to handle LocalDateTime serialization
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
        lenient().when(jwtUtl.getEmail(any())).thenReturn(USER_EMAIL);
        lenient().when(jwtUtl.getPreferredUsernameFromRequest(any())).thenReturn("admin");
    }

    private ExtensionApiKeyResponse sampleApiKeyResponse() {
        ExtensionApiKeyResponse resp = new ExtensionApiKeyResponse();
        resp.setPkExtensionApiKeyId(KEY_ID);
        resp.setName("Test Key");
        resp.setStatus("ACTIVE");
        resp.setKeyPrefix("sk-abc");
        resp.setTenantId(TENANT_ID);
        resp.setExpiresAt(LocalDateTime.now().plusDays(30));
        resp.setDaysRemaining(30L);
        return resp;
    }

    private ExtensionApiKeyCreationResponse sampleCreationResponse() {
        ExtensionApiKeyCreationResponse resp = new ExtensionApiKeyCreationResponse();
        resp.setKey(sampleApiKeyResponse());
        resp.setRawKey("raw-key");
        resp.setWarning("Store securely");
        return resp;
    }

    // ==================== POST / ====================
    @Nested
    @DisplayName("POST /api/events/extension-api-keys")
    class CreateApiKeyTests {

        @Test
        @DisplayName("Happy Path – creates key and returns 201")
        void happyPath() throws Exception {
            CreateExtensionApiKeyRequest req = new CreateExtensionApiKeyRequest();
            req.setName("New Key");
            req.setDescription("desc");

            when(apiKeyService.createApiKey(any(CreateExtensionApiKeyRequest.class), eq(TENANT_ID), eq(USER_EMAIL)))
                    .thenReturn(sampleCreationResponse());

            mockMvc.perform(post("/api/events/extension-api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.key.pkExtensionApiKeyId", is(KEY_ID)))
                    .andExpect(jsonPath("$.rawKey", is("raw-key")));
        }

        @Test
        @DisplayName("Sad Path – missing tenant returns 401")
        void sadPath_unauthorized() throws Exception {
            when(jwtUtl.getTenantFromRequest(any())).thenReturn(null);

            CreateExtensionApiKeyRequest req = new CreateExtensionApiKeyRequest();
            req.setName("Key");

            mockMvc.perform(post("/api/events/extension-api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== GET / ====================
    @Nested
    @DisplayName("GET /api/events/extension-api-keys")
    class ListApiKeysTests {

        @Test
        @DisplayName("Happy Path – returns paginated keys")
        void happyPath() throws Exception {
            List<ExtensionApiKeyResponse> content = new ArrayList<>(List.of(sampleApiKeyResponse()));
            Page<ExtensionApiKeyResponse> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(apiKeyService.listApiKeys(TENANT_ID, null, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/extension-api-keys"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].pkExtensionApiKeyId", is(KEY_ID)))
                    .andExpect(jsonPath("$.totalElements", is(1)));
        }

        @Test
        @DisplayName("Happy Path – filtered by status (empty result)")
        void happyPath_withStatus() throws Exception {
            // Use an empty page with a proper Pageable and total count
            Page<ExtensionApiKeyResponse> page = new PageImpl<>(Collections.emptyList(),
                    PageRequest.of(0, 10), 0);
            when(apiKeyService.listApiKeys(TENANT_ID, "ACTIVE", 0, 10)).thenReturn(page);

            mockMvc.perform(get("/api/events/extension-api-keys")
                            .param("status", "ACTIVE")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET /id ====================
    @Nested
    @DisplayName("GET /api/events/extension-api-keys/id")
    class GetApiKeyTests {

        @Test
        @DisplayName("Happy Path – returns key details")
        void happyPath() throws Exception {
            when(apiKeyService.getApiKey(KEY_ID)).thenReturn(sampleApiKeyResponse());

            mockMvc.perform(get("/api/events/extension-api-keys/id")
                            .param("keyId", KEY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pkExtensionApiKeyId", is(KEY_ID)));
        }
    }

    // ==================== PATCH /{keyId} ====================
    @Nested
    @DisplayName("PATCH /api/events/extension-api-keys/{keyId}")
    class UpdateApiKeyTests {

        @Test
        @DisplayName("Happy Path – updates key")
        void happyPath() throws Exception {
            UpdateExtensionApiKeyRequest req = new UpdateExtensionApiKeyRequest();
            req.setName("Updated Name");

            ExtensionApiKeyResponse updated = sampleApiKeyResponse();
            updated.setName("Updated Name");
            when(apiKeyService.updateApiKey(eq(KEY_ID), any(UpdateExtensionApiKeyRequest.class), eq(USER_EMAIL)))
                    .thenReturn(updated);

            mockMvc.perform(patch("/api/events/extension-api-keys/{keyId}", KEY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Updated Name")));
        }
    }

    // ==================== POST /{keyId}/revoke ====================
    @Nested
    @DisplayName("POST /api/events/extension-api-keys/{keyId}/revoke")
    class RevokeApiKeyTests {

        @Test
        @DisplayName("Happy Path – revokes key")
        void happyPath() throws Exception {
            doNothing().when(apiKeyService).revokeApiKey(KEY_ID, USER_EMAIL);

            mockMvc.perform(post("/api/events/extension-api-keys/{keyId}/revoke", KEY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", containsString("revoked")))
                    .andExpect(jsonPath("$.keyId", is(KEY_ID)))
                    .andExpect(jsonPath("$.status", is("REVOKED")));
        }
    }

    // ==================== POST /{keyId}/reactivate ====================
    @Nested
    @DisplayName("POST /api/events/extension-api-keys/{keyId}/reactivate")
    class ReactivateApiKeyTests {

        @Test
        @DisplayName("Happy Path – reactivates key")
        void happyPath() throws Exception {
            ExtensionApiKeyResponse reactivated = sampleApiKeyResponse();
            reactivated.setStatus("ACTIVE");
            when(apiKeyService.reactivateApiKey(KEY_ID, USER_EMAIL)).thenReturn(reactivated);

            mockMvc.perform(post("/api/events/extension-api-keys/{keyId}/reactivate", KEY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ACTIVE")));
        }
    }

    // ==================== DELETE /{keyId} ====================
    @Nested
    @DisplayName("DELETE /api/events/extension-api-keys/{keyId}")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("Happy Path – deletes key")
        void happyPath() throws Exception {
            doNothing().when(apiKeyService).deleteApiKey(KEY_ID);

            mockMvc.perform(delete("/api/events/extension-api-keys/{keyId}", KEY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", containsString("deleted")))
                    .andExpect(jsonPath("$.keyId", is(KEY_ID)));
        }
    }

    // ==================== POST /{keyId}/rotate ====================
    @Nested
    @DisplayName("POST /api/events/extension-api-keys/{keyId}/rotate")
    class RotateApiKeyTests {

        @Test
        @DisplayName("Happy Path – rotates key")
        void happyPath() throws Exception {
            RotateExtensionApiKeyRequest req = new RotateExtensionApiKeyRequest();
            req.setReason("Routine rotation");
            req.setRotationType("manual");

            when(apiKeyService.rotateApiKey(eq(KEY_ID), any(RotateExtensionApiKeyRequest.class),
                    eq(USER_EMAIL), anyString())).thenReturn(sampleCreationResponse());

            mockMvc.perform(post("/api/events/extension-api-keys/{keyId}/rotate", KEY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawKey", is("raw-key")))
                    .andExpect(jsonPath("$.key.pkExtensionApiKeyId", is(KEY_ID)));
        }
    }

    // ==================== POST /{keyId}/extend-expiry ====================
    @Nested
    @DisplayName("POST /api/events/extension-api-keys/{keyId}/extend-expiry")
    class ExtendExpiryTests {

        @Test
        @DisplayName("Happy Path – extends expiry")
        void happyPath() throws Exception {
            ExtendExtensionApiKeyExpiryRequest req = new ExtendExtensionApiKeyExpiryRequest();
            req.setNewExpiryDate(LocalDateTime.now().plusDays(60));

            ExtensionApiKeyResponse extended = sampleApiKeyResponse();
            extended.setExpiresAt(req.getNewExpiryDate());
            extended.setDaysRemaining(60L);
            when(apiKeyService.extendExpiry(eq(KEY_ID), any(ExtendExtensionApiKeyExpiryRequest.class), eq(USER_EMAIL)))
                    .thenReturn(extended);

            mockMvc.perform(post("/api/events/extension-api-keys/{keyId}/extend-expiry", KEY_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.daysRemaining", is(60)));
        }
    }

    // ==================== GET /{keyId}/rotation-history ====================
    @Nested
    @DisplayName("GET /api/events/extension-api-keys/{keyId}/rotation-history")
    class GetRotationHistoryTests {

        @Test
        @DisplayName("Happy Path – returns history list")
        void happyPath() throws Exception {
            when(apiKeyService.getRotationHistory(KEY_ID))
                    .thenReturn(List.of(new ExtensionApiKeyRotationHistory()));

            mockMvc.perform(get("/api/events/extension-api-keys/{keyId}/rotation-history", KEY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size()", is(1)));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/extension-api-keys/stats")
    class GetStatisticsTests {

        @Test
        @DisplayName("Happy Path – returns stats")
        void happyPath() throws Exception {
            ApiKeyStatisticsResponse stats = new ApiKeyStatisticsResponse();
            stats.setTotalKeys(10L);
            stats.setActiveKeys(5L);
            when(apiKeyService.getStatistics(TENANT_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/events/extension-api-keys/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalKeys", is(10)))
                    .andExpect(jsonPath("$.activeKeys", is(5)));
        }
    }

    // ==================== GET /settings ====================
    @Nested
    @DisplayName("GET /api/events/extension-api-keys/settings")
    class GetSettingsTests {

        @Test
        @DisplayName("Happy Path – returns settings")
        void happyPath() throws Exception {
            Map<String, String> configMap = Map.of("DEFAULT_EXPIRY_DAYS", "90");
            when(configService.getAllConfigurations()).thenReturn(configMap);

            mockMvc.perform(get("/api/events/extension-api-keys/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.settings.DEFAULT_EXPIRY_DAYS", is("90")));
        }
    }

    // ==================== PATCH /settings ====================
    @Nested
    @DisplayName("PATCH /api/events/extension-api-keys/settings")
    class UpdateSettingsTests {

        @Test
        @DisplayName("Happy Path – updates settings")
        void happyPath() throws Exception {
            Map<String, String> updated = Map.of("DEFAULT_EXPIRY_DAYS", "180");
            UpdateApiKeySettingsRequest req = new UpdateApiKeySettingsRequest();
            req.setSettings(updated);

            doNothing().when(configService).updateConfigurations(updated, USER_EMAIL);
            when(configService.getAllConfigurations()).thenReturn(updated);

            mockMvc.perform(patch("/api/events/extension-api-keys/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.settings.DEFAULT_EXPIRY_DAYS", is("180")));
        }
    }
}