package com.secufusion.events.service;

import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.repository.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExtensionTokenService Tests")
class ExtensionTokenServiceTest {

    @Mock private ExtensionApiKeyService apiKeyService;
    @Mock private ExtensionApiKeyConfigurationService configService;
    @Mock private ExtensionApiKeyRepository apiKeyRepository;
    @Mock private DeviceUserRepository deviceUserRepository;
    @Mock private EventsGroupRepository eventsGroupRepository;
    @Mock private EventsGroupDeviceUserMappingRepository groupMappingRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private KeycloakClientService keycloakClientService;
    @Mock private RestTemplate restTemplate;   // manually injected into service

    @InjectMocks
    private ExtensionTokenService service;

    private static final String TENANT_ID = "t1";
    private static final String RAW_KEY = "raw-key";
    private static final String KEY_HASH = "hashed-key";
    private static final String CLIENT_ID = "client-1";
    private static final String CLIENT_SECRET = "secret";
    private static final String DECRYPTED_SECRET = "decrypted";
    private static final String ACCESS_TOKEN = "jwt-token";
    private static final String VALID_EMAIL = "user@example.com";

    private ExtensionApiKey apiKey;
    private DeviceUser deviceUser;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        // Inject mocked RestTemplate and set Keycloak URL
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "keycloakServerUrl", "http://localhost:8080");

        // Common entities
        apiKey = new ExtensionApiKey();
        apiKey.setPkExtensionApiKeyId("key-id");
        apiKey.setTenantId(TENANT_ID);
        apiKey.setClientId(CLIENT_ID);
        apiKey.setClientSecret(CLIENT_SECRET);
        apiKey.setStatus("ACTIVE");
        apiKey.setExpiresAt(LocalDateTime.now().plusDays(30));  // default, overridden where needed
        apiKey.setKeyHash(KEY_HASH);

        deviceUser = new DeviceUser();
        deviceUser.setPkDeviceUserId("du-1");
        deviceUser.setTenantId(TENANT_ID);
        deviceUser.setEmail(VALID_EMAIL);
        deviceUser.setUserName("User");
        deviceUser.setDisplayName("Display");
        deviceUser.setStatus("ACTIVE");
        deviceUser.setFirstSeenAt(LocalDateTime.now());
        deviceUser.setLastSeenAt(LocalDateTime.now());

        tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        tenant.setRealmName("test-realm");

        // Common happy stubs
        lenient().when(configService.getExpiryWarningThresholdDays()).thenReturn(30);
        lenient().when(keycloakClientService.decryptSecret(CLIENT_SECRET)).thenReturn(DECRYPTED_SECRET);
        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    }

    private ValidateExtensionApiKeyResponse validValidation() {
        ValidateExtensionApiKeyResponse resp = new ValidateExtensionApiKeyResponse();
        resp.setValid(true);
        return resp;
    }

    private GenerateTokenRequest buildTokenRequest() {
        GenerateTokenRequest.DeviceUserDetails details = new GenerateTokenRequest.DeviceUserDetails();
        details.setEmail(VALID_EMAIL);
        details.setUserName("User");
        details.setDisplayName("Display");
        details.setPortalUserId("portal-1");
        details.setSource("browser");

        GenerateTokenRequest req = new GenerateTokenRequest();
        req.setApiKey(RAW_KEY);
        req.setDeviceUserDetails(details);
        return req;
    }

    // ======================= validateApiKey =======================
    @Nested
    @DisplayName("validateApiKey")
    class ValidateApiKey {
        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            ValidateExtensionApiKeyResponse result = service.validateApiKey(RAW_KEY);
            assertNotNull(result);
            assertTrue(result.getValid());
        }
    }

    // ======================= generateToken =======================
    @Nested
    @DisplayName("generateToken")
    class GenerateToken {
        @Test
        @DisplayName("Happy Path — no warning (expiry far away)")
        void happyPath() throws Exception {
            // ARRANGE
            apiKey.setExpiresAt(LocalDateTime.now().plusDays(60));  // far away
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, VALID_EMAIL))
                    .thenReturn(Optional.empty());
            when(deviceUserRepository.save(any(DeviceUser.class))).thenReturn(deviceUser);

            EventsGroup defaultGroup = new EventsGroup();
            defaultGroup.setPkEventsGroupId("g1");
            defaultGroup.setName("Default");
            when(eventsGroupRepository.findByTenantIdAndIsDefaultTrue(TENANT_ID)).thenReturn(Optional.of(defaultGroup));
            when(groupMappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId("du-1", "g1")).thenReturn(false);
            when(groupMappingRepository.save(any(EventsGroupDeviceUserMapping.class))).thenReturn(null);

            Map<String, Object> tokenBody = new HashMap<>();
            tokenBody.put("access_token", ACCESS_TOKEN);
            tokenBody.put("expires_in", 3600);
            ResponseEntity<Map> respEntity = new ResponseEntity<>(tokenBody, HttpStatus.OK);
            when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(respEntity);

            // ACT
            GenerateTokenResponse result = service.generateToken(request);

            // ASSERT
            assertNotNull(result);
            assertEquals(ACCESS_TOKEN, result.getAccessToken());
            assertNull(result.getWarningMessage());   // no warning
        }

        @Test
        @DisplayName("Happy Path — expiring key triggers warning")
        void happyPath_withExpiryWarning() throws Exception {
            apiKey.setExpiresAt(LocalDateTime.now().plusDays(10));  // within threshold
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            when(deviceUserRepository.findByTenantIdAndEmail(TENANT_ID, VALID_EMAIL))
                    .thenReturn(Optional.of(deviceUser));
            when(deviceUserRepository.save(any(DeviceUser.class))).thenReturn(deviceUser);
            when(eventsGroupRepository.findByTenantIdAndIsDefaultTrue(TENANT_ID)).thenReturn(Optional.empty());

            Map<String, Object> tokenBody = new HashMap<>();
            tokenBody.put("access_token", ACCESS_TOKEN);
            when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(tokenBody, HttpStatus.OK));

            GenerateTokenResponse result = service.generateToken(request);
            assertNotNull(result.getWarningMessage());
            assertTrue(result.getWarningMessage().contains("expires in"));
        }

        @Test
        @DisplayName("Sad Path — invalid API key")
        void sadPath_invalidKey() {
            GenerateTokenRequest request = buildTokenRequest();
            ValidateExtensionApiKeyResponse invalid = new ValidateExtensionApiKeyResponse();
            invalid.setValid(false);
            invalid.setErrorMessage("Invalid key");
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(invalid);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.generateToken(request));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        @DisplayName("Sad Path — API key not found")
        void sadPath_keyNotFound() {
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.generateToken(request));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        @DisplayName("Sad Path — Keycloak returns empty body")
        void sadPath_keycloakEmptyBody() {
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));
            when(deviceUserRepository.findByTenantIdAndEmail(any(), any())).thenReturn(Optional.of(deviceUser));
            when(deviceUserRepository.save(any())).thenReturn(deviceUser);
            when(eventsGroupRepository.findByTenantIdAndIsDefaultTrue(anyString())).thenReturn(Optional.empty());

            when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.generateToken(request));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        }
    }

    // ======================= generateTokenWithSteps =======================
    @Nested
    @DisplayName("generateTokenWithSteps")
    class GenerateTokenWithSteps {
        @Test
        @DisplayName("Happy Path — all steps succeed")
        void happyPath() throws Exception {
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));
            when(deviceUserRepository.findByTenantIdAndEmail(any(), any())).thenReturn(Optional.of(deviceUser));
            when(deviceUserRepository.save(any())).thenReturn(deviceUser);
            when(eventsGroupRepository.findByTenantIdAndIsDefaultTrue(anyString())).thenReturn(Optional.empty());

            Map<String, Object> tokenBody = new HashMap<>();
            tokenBody.put("access_token", ACCESS_TOKEN);
            when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(tokenBody, HttpStatus.OK));

            TokenValidationStepResponse result = service.generateTokenWithSteps(request);
            assertTrue(result.getSuccess());
            assertNotNull(result.getTokenData());
        }

        @Test
        @DisplayName("Sad Path — step1 validation fails")
        void sadPath_step1Fail() {
            GenerateTokenRequest request = buildTokenRequest();
            ValidateExtensionApiKeyResponse invalid = new ValidateExtensionApiKeyResponse();
            invalid.setValid(false);
            invalid.setErrorMessage("Bad key");
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(invalid);

            TokenValidationStepResponse result = service.generateTokenWithSteps(request);
            assertFalse(result.getSuccess());
            assertFalse(result.getStep1ValidateApiKey().getSuccess());
        }

        @Test
        @DisplayName("Sad Path — step2 expiry fails (key expired)")
        void sadPath_step2Expired() {
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            apiKey.setExpiresAt(LocalDateTime.now().minusDays(1));  // expired
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            TokenValidationStepResponse result = service.generateTokenWithSteps(request);
            assertFalse(result.getSuccess());
            assertTrue(result.getStep1ValidateApiKey().getSuccess());
            assertFalse(result.getStep2CheckExpiry().getSuccess());
        }

        @Test
        @DisplayName("Sad Path — step3 exception")
        void sadPath_step3Exception() {
            GenerateTokenRequest request = buildTokenRequest();
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));
            when(deviceUserRepository.findByTenantIdAndEmail(any(), any())).thenReturn(Optional.of(deviceUser));
            when(deviceUserRepository.save(any())).thenReturn(deviceUser);
            when(eventsGroupRepository.findByTenantIdAndIsDefaultTrue(anyString())).thenReturn(Optional.empty());

            when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                    .thenThrow(new RuntimeException("Keycloak down"));

            TokenValidationStepResponse result = service.generateTokenWithSteps(request);
            assertFalse(result.getSuccess());
            assertTrue(result.getStep1ValidateApiKey().getSuccess());
            assertTrue(result.getStep2CheckExpiry().getSuccess());
            assertFalse(result.getStep3GenerateToken().getSuccess());
        }
    }

    // ======================= checkExpiry =======================
    @Nested
    @DisplayName("checkExpiry")
    class CheckExpiry {
        @Test
        @DisplayName("Happy Path — not expiring")
        void happyPath_notExpiring() {
            // Expiry far away => not expiring soon
            apiKey.setExpiresAt(LocalDateTime.now().plusDays(60));
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            ApiKeyExpiryCheckResponse result = service.checkExpiry(RAW_KEY);
            assertTrue(result.getValid());
            assertFalse(result.getExpiringSoon());
            assertNull(result.getWarningMessage());
        }

        @Test
        @DisplayName("Happy Path — expiring soon")
        void happyPath_expiringSoon() {
            apiKey.setExpiresAt(LocalDateTime.now().plusDays(10));
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            ApiKeyExpiryCheckResponse result = service.checkExpiry(RAW_KEY);
            assertTrue(result.getExpiringSoon());
            assertNotNull(result.getWarningMessage());
        }

        @Test
        @DisplayName("Sad Path — invalid key")
        void sadPath_invalid() {
            ValidateExtensionApiKeyResponse invalid = new ValidateExtensionApiKeyResponse();
            invalid.setValid(false);
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(invalid);

            ApiKeyExpiryCheckResponse result = service.checkExpiry(RAW_KEY);
            assertFalse(result.getValid());
            assertNull(result.getKeyId());
        }
    }

    // ======================= getUsageStats =======================
    @Nested
    @DisplayName("getUsageStats")
    class GetUsageStats {
        @Test
        @DisplayName("Happy Path — returns user stats")
        void happyPath() {
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(validValidation());
            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKey));

            List<DeviceUser> users = List.of(deviceUser);
            when(deviceUserRepository.findByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(users);
            when(groupMappingRepository.findActiveGroupsForDeviceUser("du-1", TENANT_ID))
                    .thenReturn(List.of(new EventsGroupDeviceUserMapping()));

            ApiKeyUsageStatsResponse result = service.getUsageStats(RAW_KEY);
            assertEquals(1, result.getTotalUsers());
            assertEquals(1, result.getActiveUsers());
        }

        @Test
        @DisplayName("Sad Path — invalid key")
        void sadPath_invalidKey() {
            ValidateExtensionApiKeyResponse invalid = new ValidateExtensionApiKeyResponse();
            invalid.setValid(false);
            when(apiKeyService.validateApiKeyInternal(RAW_KEY)).thenReturn(invalid);

            assertThrows(ResponseStatusException.class, () -> service.getUsageStats(RAW_KEY));
        }
    }
}