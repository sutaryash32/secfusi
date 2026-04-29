package com.secufusion.events.service;

import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.repository.*;
import com.secufusion.events.util.ApiKeyUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.ClientRepresentation;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExtensionApiKeyService Tests")
class ExtensionApiKeyServiceTest {

    @Mock private ExtensionApiKeyRepository apiKeyRepository;
    @Mock private ExtensionApiKeyRotationHistoryRepository rotationHistoryRepository;
    @Mock private ExtensionApiKeyConfigurationService configService;
    @Mock private TenantRepository tenantRepository;
    @Mock private KeycloakClientService keycloakClientService;

    @InjectMocks
    private ExtensionApiKeyService service;

    private MockedStatic<ApiKeyUtil> apiKeyUtilMock;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_EMAIL = "admin@example.com";
    private static final String RAW_KEY = "raw-api-key-64chars...";
    private static final String KEY_HASH = "hashed-key";
    private static final String KEY_PREFIX = "sk-abc";
    private static final String KEY_ID = "key-123";
    private static final String ENCRYPTED_SECRET = "encrypted-secret";
    private static final String CLIENT_SECRET_PLAIN = "plain-secret";

    private Tenant testTenant;
    private ExtensionApiKey testApiKey;
    private String expectedClientId;        // <-- computed like the service does

    @BeforeEach
    void setUp() {
        apiKeyUtilMock = mockStatic(ApiKeyUtil.class);
        apiKeyUtilMock.when(() -> ApiKeyUtil.generateApiKey(64)).thenReturn(RAW_KEY);
        apiKeyUtilMock.when(() -> ApiKeyUtil.hash(RAW_KEY)).thenReturn(KEY_HASH);
        apiKeyUtilMock.when(() -> ApiKeyUtil.extractPrefix(RAW_KEY)).thenReturn(KEY_PREFIX);

        testTenant = new Tenant();
        testTenant.setTenantID(TENANT_ID);
        testTenant.setTenantName("TenantName");
        testTenant.setRealmName("tenant-realm");
        testTenant.setDomain("example.com");

        // Service logic: tenantName.toLowerCase().replaceAll("\\s+", "-") + "-extension-client"
        expectedClientId = testTenant.getTenantName()
                .toLowerCase()
                .replaceAll("\\s+", "-")
                + "-extension-client";   // -> "tenantname-extension-client"

        testApiKey = new ExtensionApiKey();
        testApiKey.setPkExtensionApiKeyId(KEY_ID);
        testApiKey.setTenantId(TENANT_ID);
        testApiKey.setKeyHash(KEY_HASH);
        testApiKey.setKeyPrefix(KEY_PREFIX);
        testApiKey.setClientId(expectedClientId);          // use the computed value
        testApiKey.setClientSecret(ENCRYPTED_SECRET);
        testApiKey.setName("Test Key");
        testApiKey.setDescription("A test key");
        testApiKey.setStatus("ACTIVE");
        testApiKey.setExpiresAt(LocalDateTime.now().plusDays(30));
        testApiKey.setCreatedBy(USER_EMAIL);
        testApiKey.setCreatedAt(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
        testApiKey.setUpdatedAt(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());

        lenient().when(configService.getMaxKeysPerTenant()).thenReturn(5);
        lenient().when(configService.getDefaultExpiryDays()).thenReturn(30);
        lenient().when(configService.getExpiryWarningThresholdDays()).thenReturn(15);
        lenient().when(configService.isExpiryExtensionAllowed()).thenReturn(true);
        lenient().when(configService.getMaxExpiryExtensionDays()).thenReturn(365);
    }

    @AfterEach
    void tearDown() {
        apiKeyUtilMock.close();
    }

    // ==================== createApiKey ====================
    @Nested
    @DisplayName("createApiKey")
    class CreateApiKeyTests {

        @Test
        @DisplayName("Happy Path — key created with new Keycloak client")
        void happyPath_newClient() {
            CreateExtensionApiKeyRequest req = new CreateExtensionApiKeyRequest();
            req.setName("New Key");
            req.setDescription("Desc");

            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(0L);

            when(keycloakClientService.clientExists("tenant-realm", expectedClientId))
                    .thenReturn(false);
            doNothing().when(keycloakClientService)
                    .createClient(eq("tenant-realm"), any(ClientRepresentation.class));

            ClientRepresentation fetchedClient = new ClientRepresentation();
            fetchedClient.setSecret(CLIENT_SECRET_PLAIN);
            when(keycloakClientService.getClientWithSecret("tenant-realm", expectedClientId))
                    .thenReturn(fetchedClient);
            when(keycloakClientService.encryptSecret(CLIENT_SECRET_PLAIN))
                    .thenReturn(ENCRYPTED_SECRET);

            when(apiKeyRepository.save(any(ExtensionApiKey.class))).thenAnswer(inv -> {
                ExtensionApiKey key = inv.getArgument(0);
                key.setPkExtensionApiKeyId(KEY_ID);
                return key;
            });

            ExtensionApiKeyCreationResponse response = service.createApiKey(req, TENANT_ID, USER_EMAIL);

            assertNotNull(response);
            assertEquals(RAW_KEY, response.getRawKey());
            assertEquals(KEY_ID, response.getKey().getPkExtensionApiKeyId());
            verify(keycloakClientService).createClient(eq("tenant-realm"),
                    any(ClientRepresentation.class));
        }

        @Test
        @DisplayName("Happy Path — existing Keycloak client reused")
        void happyPath_existingClient() {
            CreateExtensionApiKeyRequest req = new CreateExtensionApiKeyRequest();
            req.setName("New Key");

            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(0L);

            when(keycloakClientService.clientExists("tenant-realm", expectedClientId))
                    .thenReturn(true);
            ClientRepresentation existingClient = new ClientRepresentation();
            existingClient.setSecret(CLIENT_SECRET_PLAIN);
            when(keycloakClientService.getClientWithSecret("tenant-realm", expectedClientId))
                    .thenReturn(existingClient);
            when(keycloakClientService.encryptSecret(CLIENT_SECRET_PLAIN))
                    .thenReturn(ENCRYPTED_SECRET);

            when(apiKeyRepository.save(any(ExtensionApiKey.class))).thenAnswer(inv -> {
                ExtensionApiKey key = inv.getArgument(0);
                key.setPkExtensionApiKeyId(KEY_ID);
                return key;
            });

            ExtensionApiKeyCreationResponse response = service.createApiKey(req, TENANT_ID, USER_EMAIL);

            assertNotNull(response);
            // No Keycloak client creation should happen
            verify(keycloakClientService, never()).createClient(anyString(),
                    any(ClientRepresentation.class));
        }

        @Test
        @DisplayName("Sad Path — tenant not found")
        void sadPath_tenantNotFound() {
            CreateExtensionApiKeyRequest req = new CreateExtensionApiKeyRequest();
            req.setName("Key");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.createApiKey(req, TENANT_ID, USER_EMAIL));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("Sad Path — max active keys reached")
        void sadPath_maxKeysReached() {
            CreateExtensionApiKeyRequest req = new CreateExtensionApiKeyRequest();
            req.setName("Key");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(5L);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.createApiKey(req, TENANT_ID, USER_EMAIL));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }

    // ==================== getApiKey ====================
    @Nested
    @DisplayName("getApiKey")
    class GetApiKeyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));
            ExtensionApiKeyResponse result = service.getApiKey(KEY_ID);
            assertEquals(KEY_ID, result.getPkExtensionApiKeyId());
            assertEquals("Test Key", result.getName());
        }

        @Test
        @DisplayName("Sad Path — not found")
        void sadPath_notFound() {
            when(apiKeyRepository.findById("bad")).thenReturn(Optional.empty());
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.getApiKey("bad"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    // ==================== listApiKeys ====================
    @Nested
    @DisplayName("listApiKeys")
    class ListApiKeysTests {

        @Test
        @DisplayName("Happy Path — all keys")
        void happyPath_all() {
            Page<ExtensionApiKey> page = new PageImpl<>(List.of(testApiKey));
            when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(eq(TENANT_ID), any(Pageable.class)))
                    .thenReturn(page);
            Page<ExtensionApiKeyResponse> result = service.listApiKeys(TENANT_ID, null, 0, 10);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Happy Path — filtered by status")
        void happyPath_filtered() {
            Page<ExtensionApiKey> page = new PageImpl<>(List.of(testApiKey));
            when(apiKeyRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(eq(TENANT_ID), eq("ACTIVE"), any(Pageable.class)))
                    .thenReturn(page);
            Page<ExtensionApiKeyResponse> result = service.listApiKeys(TENANT_ID, "ACTIVE", 0, 10);
            assertEquals(1, result.getTotalElements());
        }
    }

    // ==================== updateApiKey ====================
    @Nested
    @DisplayName("updateApiKey")
    class UpdateApiKeyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            UpdateExtensionApiKeyRequest req = new UpdateExtensionApiKeyRequest();
            req.setName("Updated Name");
            req.setDescription("New desc");

            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any(ExtensionApiKey.class))).thenReturn(testApiKey);

            ExtensionApiKeyResponse result = service.updateApiKey(KEY_ID, req, USER_EMAIL);
            assertEquals("Updated Name", result.getName());
        }

        @Test
        @DisplayName("Sad Path — revoked key cannot be updated")
        void sadPath_revoked() {
            testApiKey.setStatus("REVOKED");
            UpdateExtensionApiKeyRequest req = new UpdateExtensionApiKeyRequest();
            req.setName("New");
            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.updateApiKey(KEY_ID, req, USER_EMAIL));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }

    // ==================== revokeApiKey ====================
    @Nested
    @DisplayName("revokeApiKey")
    class RevokeApiKeyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any())).thenReturn(testApiKey);

            assertDoesNotThrow(() -> service.revokeApiKey(KEY_ID, USER_EMAIL));
            assertEquals("REVOKED", testApiKey.getStatus());
        }

        @Test
        @DisplayName("Sad Path — not found")
        void sadPath_notFound() {
            when(apiKeyRepository.findById("bad")).thenReturn(Optional.empty());
            assertThrows(ResponseStatusException.class, () -> service.revokeApiKey("bad", USER_EMAIL));
        }
    }

    // ==================== reactivateApiKey ====================
    @Nested
    @DisplayName("reactivateApiKey")
    class ReactivateApiKeyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            testApiKey.setStatus("INACTIVE");
            testApiKey.setExpiresAt(LocalDateTime.now().plusDays(10));
            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any())).thenReturn(testApiKey);

            ExtensionApiKeyResponse result = service.reactivateApiKey(KEY_ID, USER_EMAIL);
            assertEquals("ACTIVE", result.getStatus());
        }

        @Test
        @DisplayName("Sad Path — key expired cannot be reactivated")
        void sadPath_expired() {
            testApiKey.setStatus("INACTIVE");
            testApiKey.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.reactivateApiKey(KEY_ID, USER_EMAIL));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }

    // ==================== deleteApiKey ====================
    @Nested
    @DisplayName("deleteApiKey")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(apiKeyRepository.existsById(KEY_ID)).thenReturn(true);
            doNothing().when(apiKeyRepository).deleteById(KEY_ID);
            assertDoesNotThrow(() -> service.deleteApiKey(KEY_ID));
            verify(apiKeyRepository).deleteById(KEY_ID);
        }

        @Test
        @DisplayName("Sad Path — not found")
        void sadPath_notFound() {
            when(apiKeyRepository.existsById("bad")).thenReturn(false);
            assertThrows(ResponseStatusException.class, () -> service.deleteApiKey("bad"));
        }
    }

    // ==================== rotateApiKey ====================
    @Nested
    @DisplayName("rotateApiKey")
    class RotateApiKeyTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            RotateExtensionApiKeyRequest req = new RotateExtensionApiKeyRequest();
            req.setReason("Routine rotation");
            req.setRotationType("manual");

            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any(ExtensionApiKey.class))).thenAnswer(inv -> inv.getArgument(0));
            when(rotationHistoryRepository.save(any())).thenReturn(new ExtensionApiKeyRotationHistory());

            ExtensionApiKeyCreationResponse response = service.rotateApiKey(KEY_ID, req, USER_EMAIL, "127.0.0.1");

            assertNotNull(response);
            assertEquals(RAW_KEY, response.getRawKey());
            assertEquals("REVOKED", testApiKey.getStatus());
            verify(apiKeyRepository, times(2)).save(any(ExtensionApiKey.class));
        }

        @Test
        @DisplayName("Sad Path — key not found")
        void sadPath_notFound() {
            RotateExtensionApiKeyRequest req = new RotateExtensionApiKeyRequest();
            when(apiKeyRepository.findById("bad")).thenReturn(Optional.empty());
            assertThrows(ResponseStatusException.class,
                    () -> service.rotateApiKey("bad", req, USER_EMAIL, "127.0.0.1"));
        }
    }

    // ==================== extendExpiry ====================
    @Nested
    @DisplayName("extendExpiry")
    class ExtendExpiryTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            ExtendExtensionApiKeyExpiryRequest req = new ExtendExtensionApiKeyExpiryRequest();
            LocalDateTime newExpiry = LocalDateTime.now().plusDays(60);
            req.setNewExpiryDate(newExpiry);

            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any())).thenReturn(testApiKey);

            ExtensionApiKeyResponse result = service.extendExpiry(KEY_ID, req, USER_EMAIL);
            assertNotNull(result);
            verify(apiKeyRepository).save(any());
        }

        @Test
        @DisplayName("Sad Path — extension not allowed by policy")
        void sadPath_extensionNotAllowed() {
            lenient().when(configService.isExpiryExtensionAllowed()).thenReturn(false);
            ExtendExtensionApiKeyExpiryRequest req = new ExtendExtensionApiKeyExpiryRequest();
            req.setNewExpiryDate(LocalDateTime.now().plusDays(1));
            when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(testApiKey));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.extendExpiry(KEY_ID, req, USER_EMAIL));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }
    }

    // ==================== getRotationHistory ====================
    @Nested
    @DisplayName("getRotationHistory")
    class GetRotationHistoryTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(rotationHistoryRepository.findRotationChain(KEY_ID))
                    .thenReturn(List.of(new ExtensionApiKeyRotationHistory()));
            List<ExtensionApiKeyRotationHistory> result = service.getRotationHistory(KEY_ID);
            assertEquals(1, result.size());
        }
    }

    // ==================== getStatistics ====================
    @Nested
    @DisplayName("getStatistics")
    class GetStatisticsTests {

        @Test
        @DisplayName("Happy Path — tenant-level stats")
        void happyPath() {
            when(apiKeyRepository.countByTenantId(TENANT_ID)).thenReturn(10L);
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(6L);
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "INACTIVE")).thenReturn(1L);
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "REVOKED")).thenReturn(2L);
            when(apiKeyRepository.countByTenantIdAndStatus(TENANT_ID, "EXPIRED")).thenReturn(1L);
            when(apiKeyRepository.countKeysExpiringSoon(eq(TENANT_ID), any(), any())).thenReturn(3L);

            ApiKeyStatisticsResponse stats = service.getStatistics(TENANT_ID);
            assertEquals(10L, stats.getTotalKeys());
            assertEquals(6L, stats.getActiveKeys());
            assertEquals(3L, stats.getExpiringSoonKeys());
        }
    }

    // ==================== validateApiKeyInternal ====================
    @Nested
    @DisplayName("validateApiKeyInternal")
    class ValidateApiKeyInternalTests {

        @Test
        @DisplayName("Happy Path — valid key")
        void happyPath_valid() {
            when(apiKeyRepository.findByKeyHash(KEY_HASH)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any())).thenReturn(testApiKey);

            ValidateExtensionApiKeyResponse result = service.validateApiKeyInternal(RAW_KEY);
            assertTrue(result.getValid());
            assertEquals(TENANT_ID, result.getTenantId());
        }

        @Test
        @DisplayName("Sad Path — key not found")
        void sadPath_notFound() {
            when(apiKeyRepository.findByKeyHash(KEY_HASH)).thenReturn(Optional.empty());
            ValidateExtensionApiKeyResponse result = service.validateApiKeyInternal(RAW_KEY);
            assertFalse(result.getValid());
            assertEquals("INVALID_KEY", result.getErrorCode());
        }

        @Test
        @DisplayName("Sad Path — key expired")
        void sadPath_expired() {
            testApiKey.setStatus("ACTIVE");
            testApiKey.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(apiKeyRepository.findByKeyHash(KEY_HASH)).thenReturn(Optional.of(testApiKey));
            when(apiKeyRepository.save(any())).thenReturn(testApiKey);

            ValidateExtensionApiKeyResponse result = service.validateApiKeyInternal(RAW_KEY);
            assertFalse(result.getValid());
            assertEquals("KEY_EXPIRED", result.getErrorCode());
        }
    }
}