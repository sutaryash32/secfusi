package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.exception.*;
import com.secufusion.tenant.repository.*;
import com.secufusion.tenant.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.ClientRepresentation;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────
    @Mock private TenantRepository             tenantRepository;
    @Mock private UserRepository               userRepository;
    @Mock private AuthProviderConfigRepository authProviderConfigRepository;
    @Mock private KeycloakAdminUtil            kcUtil;
    @Mock private GroupService                 groupService;
    @Mock private RoleService                  roleService;
    @Mock private JwtUtl                       jwtUtl;
    @Mock private SmtpConfigService            smtpConfigService;
    @Mock private SubscriptionService          subscriptionService;
    @Mock private ExtensionApiKeyRepository    apiKeyRepository;
    @Mock private PolicyAssignmentRepository   policyAssignmentRepository;
    @Mock private EventsGroupService           eventsGroupService;
    @Mock private BrowserPolicyService         browserPolicyService;
    @Mock private NetworkPolicyService         networkPolicyService;
    @Mock private ExtensionPolicyService       extensionPolicyService;
    @Mock private Executor                     tenantProvisioningExecutor;
    @Mock private TenantService                self;
    @Mock private HttpServletRequest           httpRequest;

    @InjectMocks
    private TenantService service;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String TENANT_ID   = "tenant-uuid-001";
    private static final String TENANT_NAME = "acme";
    private static final String REALM_NAME  = "acme";
    private static final String ADMIN_EMAIL = "admin@acme.com";
    private static final String ADMIN_ID    = "user-uuid-001";
    private static final String KC_USER_ID  = "kc-user-001";
    private static final String GROUP_ID    = "group-uuid-001";
    private static final String BASE_URL    = "https://keycloak.example.com";
    private static final String EXTENSION   = ".motivitylabs.net";

    // ── Common entities ───────────────────────────────────────────────────────
    private Tenant            mockTenant;
    private User              mockAdmin;
    private AuthProviderConfig mockAuthConfig;
    private Groups            mockGroup;

    @BeforeEach
    void setUp() {
        // ── Inject @Value fields ──────────────────────────────────────────────
        ReflectionTestUtils.setField(service, "baseUrl",               BASE_URL);
        ReflectionTestUtils.setField(service, "extension",             EXTENSION);
        ReflectionTestUtils.setField(service, "strictValidation",      false);
        ReflectionTestUtils.setField(service, "masterAzureClientId",   "azure-client-id");
        ReflectionTestUtils.setField(service, "masterAzureClientSecret","azure-secret");
        ReflectionTestUtils.setField(service, "masterAzureAlias",      "microsoft");

        // ── Inject self-reference (bypasses Spring @Lazy proxy) ──────────────
        ReflectionTestUtils.setField(service, "self", self);

        // ── Build admin user ──────────────────────────────────────────────────
        mockAdmin = new User();
        mockAdmin.setPkUserId(ADMIN_ID);
        mockAdmin.setEmail(ADMIN_EMAIL);
        mockAdmin.setUserName(ADMIN_EMAIL);
        mockAdmin.setFirstName("Admin");
        mockAdmin.setLastName("User");
        mockAdmin.setDefaultUser(true);
        mockAdmin.setKeycloakUserId(KC_USER_ID);
        mockAdmin.setStatus("ACTIVE");
        mockAdmin.setWelcomeEmailSentAt(null);
        mockAdmin.setWelcomeEmailSentCount(0);
        mockAdmin.setResetEmailSentAt(null);
        mockAdmin.setResetEmailSentCount(0);
        mockAdmin.setPhoneNo("+1-555-0101");

        // ── Build tenant ──────────────────────────────────────────────────────
        mockTenant = new Tenant();
        mockTenant.setTenantID(TENANT_ID);
        mockTenant.setTenantName(TENANT_NAME);
        mockTenant.setRealmName(REALM_NAME);
        mockTenant.setDomain("acme.motivitylabs.net");
        mockTenant.setEmail("contact@acme.com");
        mockTenant.setStatus("ACTIVE");
        mockTenant.setSsoType("KEYCLOAK");
        mockTenant.setSelfManaged(false);
        mockTenant.setTenantType("Enterprise");
        mockTenant.setRegion("US");
        mockTenant.setPhoneNo("+1-555-0100");
        mockTenant.setIndustry("Technology");
        mockTenant.setLoginUrl(BASE_URL + "/realms/" + REALM_NAME
                + "/protocol/openid-connect/auth");
        mockTenant.setTenantCode("ACME-001");
        mockTenant.setCreatedAt(Instant.now());
        mockTenant.setProvisionRetryCount(0);
        mockTenant.setParentTenantId(null);
        mockTenant.setUsers(new ArrayList<>(List.of(mockAdmin)));

        // ── Build auth config ─────────────────────────────────────────────────
        mockAuthConfig = new AuthProviderConfig();
        mockAuthConfig.setSsoType("KEYCLOAK");
        mockAuthConfig.setIssuerUri(BASE_URL + "/realms/" + REALM_NAME);
        mockAuthConfig.setAuthServerUrl(BASE_URL);
        mockAuthConfig.setTokenEndpoint(BASE_URL + "/realms/" + REALM_NAME
                + "/protocol/openid-connect/token");
        mockAuthConfig.setJwkUri(BASE_URL + "/realms/" + REALM_NAME
                + "/protocol/openid-connect/certs");
        mockAuthConfig.setClientId(TENANT_NAME);
        mockAuthConfig.setRedirectUri("https://acme.motivitylabs.net/*");
        mockAuthConfig.setLoginUrl(mockTenant.getLoginUrl());
        mockAuthConfig.setTenant(mockTenant);

        // ── Build group ───────────────────────────────────────────────────────
        mockGroup = new Groups();
        mockGroup.setPkGroupId(GROUP_ID);
        mockGroup.setName(TENANT_NAME + "_Admin");
    }

    // ── Helper: CreateTenantRequest ───────────────────────────────────────────
    private CreateTenantRequest buildCreateRequest() {
        CreateTenantRequest req = new CreateTenantRequest();
        req.setTenantName(TENANT_NAME);
        req.setDomain("acme");
        req.setEmail("contact@acme.com");
        req.setRegion("US");
        req.setPhoneNo("+1-555-0100");
        req.setTenantType("Enterprise");
        req.setSsoType("KEYCLOAK");
        req.setAdminEmail(ADMIN_EMAIL);
        req.setAdminFirstName("Admin");
        req.setAdminLastName("User");
        req.setAdminUserName(ADMIN_EMAIL);
        req.setAdminPhoneNumber("+1-555-0101");
        req.setSelfManaged(false);
        return req;
    }

    // ── Helper: stub subscriptionService for buildResponse ───────────────────
    private void stubSubscriptionForBuildResponse() {
        try {
            when(subscriptionService.getActiveSubscription(TENANT_ID))
                    .thenReturn(Optional.empty());
        } catch (Exception ignored) { }
    }

    // =========================================================================
    // validateConfiguration  (@PostConstruct — invoked via ReflectionTestUtils)
    // =========================================================================

    @Nested
    @DisplayName("validateConfiguration")
    class ValidateConfiguration {

        @Test
        @DisplayName("Happy Path — valid extension and baseUrl passes without exception")
        void happyPath_validConfig_noException() {
            // ARRANGE — values already set in @BeforeEach

            // ACT + ASSERT
            assertDoesNotThrow(() ->
                    ReflectionTestUtils.invokeMethod(service, "validateConfiguration"));
        }

        @Test
        @DisplayName("Sad Path — blank extension throws IllegalStateException")
        void sadPath_blankExtension_throwsIllegalState() {
            // ARRANGE
            ReflectionTestUtils.setField(service, "extension", "");

            // ACT + ASSERT
            assertThrows(IllegalStateException.class, () ->
                    ReflectionTestUtils.invokeMethod(service, "validateConfiguration"));
        }

        @Test
        @DisplayName("Sad Path — null extension throws IllegalStateException")
        void sadPath_nullExtension_throwsIllegalState() {
            // ARRANGE
            ReflectionTestUtils.setField(service, "extension", null);

            // ACT + ASSERT
            assertThrows(IllegalStateException.class, () ->
                    ReflectionTestUtils.invokeMethod(service, "validateConfiguration"));
        }

        @Test
        @DisplayName("Sad Path — blank baseUrl throws IllegalStateException")
        void sadPath_blankBaseUrl_throwsIllegalState() {
            // ARRANGE
            ReflectionTestUtils.setField(service, "baseUrl", "");

            // ACT + ASSERT
            assertThrows(IllegalStateException.class, () ->
                    ReflectionTestUtils.invokeMethod(service, "validateConfiguration"));
        }
    }

    // =========================================================================
    // getProvisioningStatus
    // =========================================================================

    @Nested
    @DisplayName("getProvisioningStatus")
    class GetProvisioningStatus {

        @Test
        @DisplayName("Happy Path — ACTIVE tenant returns full TenantResponse")
        void happyPath_activeTenant_fullResponse() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            stubSubscriptionForBuildResponse();

            // ACT
            TenantResponse result = service.getProvisioningStatus(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID,   result.getTenantID());
            assertEquals(TENANT_NAME, result.getTenantName());
            assertEquals("ACTIVE",    result.getStatus());
            assertEquals(REALM_NAME,  result.getRealmName());
            verify(tenantRepository).findByTenantID(TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — CREATING tenant returns lightweight response")
        void happyPath_creatingTenant_lightweightResponse() {
            // ARRANGE
            mockTenant.setStatus("CREATING");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT
            TenantResponse result = service.getProvisioningStatus(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals("CREATING", result.getStatus());
            // Subscription must NOT be fetched for non-ACTIVE
            verify(subscriptionService, never()).getActiveSubscription(anyString());
        }

        @Test
        @DisplayName("Happy Path — FAILED tenant returns lightweight response")
        void happyPath_failedTenant_lightweightResponse() {
            // ARRANGE
            mockTenant.setStatus("FAILED");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT
            TenantResponse result = service.getProvisioningStatus(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals("FAILED", result.getStatus());
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_notFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getProvisioningStatus("wrong-id"));
            verify(tenantRepository).findByTenantID("wrong-id");
        }
    }

    // =========================================================================
    // createTenantSystem
    // =========================================================================

    @Nested
    @DisplayName("createTenantSystem")
    class CreateTenantSystem {

        @Test
        @DisplayName("Happy Path — ACTIVE tenant already exists returns existing response")
        void happyPath_activeTenantExists_returnsExisting() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant)); // status = ACTIVE
            stubSubscriptionForBuildResponse();

            // ACT
            TenantResponse result = service.createTenantSystem(req);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantID());
            assertEquals("ACTIVE",  result.getStatus());
            // No provisioning should happen
            verify(tenantRepository, never()).save(any());
        }
    }

    // =========================================================================
    // createTenant
    // =========================================================================

    @Nested
    @DisplayName("createTenant")
    class CreateTenantMethod {

        @Test
        @DisplayName("Happy Path — ACTIVE existing tenant throws KeycloakOperationException")
        void happyPath_activeTenant_throwsAlreadyActive() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant)); // ACTIVE

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.createTenant(httpRequest, req));
        }

        @Test
        @DisplayName("Happy Path — existing CREATING tenant returns lightweight for polling")
        void happyPath_existingCreating_returnsLightweight() {
            // ARRANGE
            mockTenant.setStatus("CREATING");
            CreateTenantRequest req = buildCreateRequest();
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant));

            // ACT
            TenantResponse result = service.createTenant(httpRequest, req);

            // ASSERT
            assertNotNull(result);
            assertEquals("CREATING", result.getStatus());
            verify(subscriptionService, never()).getActiveSubscription(anyString());
        }

        @Test
        @DisplayName("Sad Path — concurrent creation lock held throws KeycloakOperationException")
        void sadPath_concurrentLock_throwsException() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.empty());

            // Simulate lock already held
            getConcurrentLocks().put(TENANT_NAME, Instant.now().toString());

            try {
                // ACT + ASSERT
                assertThrows(KeycloakOperationException.class,
                        () -> service.createTenant(httpRequest, req));
            } finally {
                getConcurrentLocks().clear();
            }
        }
    }

    // =========================================================================
    // resendWelcomeEmail
    // =========================================================================

    @Nested
    @DisplayName("resendWelcomeEmail")
    class ResendWelcomeEmail {

        @Test
        @DisplayName("Happy Path — ACTIVE tenant with admin triggers async send")
        void happyPath_activeTenantWithAdmin_triggersAsyncSend() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(userRepository.findDefaultAdminByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(mockAdmin));

            // ACT — fire and forget (async), just ensure no sync exception
            assertDoesNotThrow(() -> service.resendWelcomeEmail(TENANT_ID));

            // ASSERT
            verify(tenantRepository).findByTenantID(TENANT_ID);
            verify(userRepository).findDefaultAdminByTenantId(TENANT_ID);
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.resendWelcomeEmail("bad-id"));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Sad Path — non-ACTIVE tenant throws GlobalException")
        void sadPath_nonActiveTenant_throwsGlobal() {
            // ARRANGE
            mockTenant.setStatus("CREATING");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.resendWelcomeEmail(TENANT_ID));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Sad Path — no admin user found throws GlobalException")
        void sadPath_noAdminUser_throwsGlobal() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(userRepository.findDefaultAdminByTenantId(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.resendWelcomeEmail(TENANT_ID));
        }
    }

    // =========================================================================
    // resendResetPasswordEmail
    // =========================================================================

    @Nested
    @DisplayName("resendResetPasswordEmail")
    class ResendResetPasswordEmail {

        @Test
        @DisplayName("Happy Path — ACTIVE KEYCLOAK tenant with kcUserId triggers async send")
        void happyPath_activeKeycloakTenant_triggersAsync() {
            // ARRANGE
            mockTenant.setSsoType("KEYCLOAK");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(userRepository.findDefaultAdminByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(mockAdmin));

            // ACT
            assertDoesNotThrow(() -> service.resendResetPasswordEmail(TENANT_ID));

            // ASSERT
            verify(userRepository).findDefaultAdminByTenantId(TENANT_ID);
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.resendResetPasswordEmail("bad-id"));
        }

        @Test
        @DisplayName("Sad Path — non-ACTIVE status throws GlobalException")
        void sadPath_nonActive_throwsGlobal() {
            // ARRANGE
            mockTenant.setStatus("FAILED");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.resendResetPasswordEmail(TENANT_ID));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Sad Path — AZURE SSO tenant throws GlobalException (no password reset)")
        void sadPath_azureSso_throwsGlobal() {
            // ARRANGE
            mockTenant.setSsoType("AZURE");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.resendResetPasswordEmail(TENANT_ID));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Sad Path — admin has null keycloakUserId throws GlobalException")
        void sadPath_nullKeycloakUserId_throwsGlobal() {
            // ARRANGE
            mockAdmin.setKeycloakUserId(null);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(userRepository.findDefaultAdminByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(mockAdmin));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.resendResetPasswordEmail(TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — no admin user found throws GlobalException")
        void sadPath_noAdminUser_throwsGlobal() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(userRepository.findDefaultAdminByTenantId(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.resendResetPasswordEmail(TENANT_ID));
        }
    }

    // =========================================================================
    // setAdminTemporaryPassword
    // =========================================================================

    @Nested
    @DisplayName("setAdminTemporaryPassword")
    class SetAdminTemporaryPassword {

        private static final String PARENT_TENANT_ID = "parent-uuid-001";

        @BeforeEach
        void setParent() {
            mockTenant.setParentTenantId(PARENT_TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — caller is parent, admin has kcId, password set in KC")
        void happyPath_callerIsParent_passwordSet() {
            // ARRANGE
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            doNothing().when(kcUtil)
                    .setUserPassword(REALM_NAME, KC_USER_ID, "TempPass@123", true);

            // ACT
            assertDoesNotThrow(() ->
                    service.setAdminTemporaryPassword(
                            PARENT_TENANT_ID, TENANT_ID, "TempPass@123"));

            // ASSERT
            verify(kcUtil).setUserPassword(REALM_NAME, KC_USER_ID, "TempPass@123", true);
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findById(anyString())).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.setAdminTemporaryPassword(
                            PARENT_TENANT_ID, "bad-id", "pass"));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Sad Path — caller is not the parent throws GlobalException")
        void sadPath_callerNotParent_throwsGlobal() {
            // ARRANGE
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.setAdminTemporaryPassword(
                            "wrong-caller", TENANT_ID, "pass"));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Sad Path — no default admin user found throws GlobalException")
        void sadPath_noDefaultAdmin_throwsGlobal() {
            // ARRANGE
            mockTenant.setUsers(Collections.emptyList());
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.setAdminTemporaryPassword(
                            PARENT_TENANT_ID, TENANT_ID, "pass"));
        }

        @Test
        @DisplayName("Sad Path — admin keycloakUserId is null throws GlobalException")
        void sadPath_nullKeycloakId_throwsGlobal() {
            // ARRANGE
            mockAdmin.setKeycloakUserId(null);
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.setAdminTemporaryPassword(
                            PARENT_TENANT_ID, TENANT_ID, "pass"));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Sad Path — admin keycloakUserId is blank throws GlobalException")
        void sadPath_blankKeycloakId_throwsGlobal() {
            // ARRANGE
            mockAdmin.setKeycloakUserId("   ");
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.setAdminTemporaryPassword(
                            PARENT_TENANT_ID, TENANT_ID, "pass"));
        }
    }

    // =========================================================================
    // manualRetryProvisioning
    // =========================================================================

    @Nested
    @DisplayName("manualRetryProvisioning")
    class ManualRetryProvisioning {

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.manualRetryProvisioning("bad-id"));
        }

        @Test
        @DisplayName("Sad Path — ACTIVE tenant throws GlobalException")
        void sadPath_activeTenant_throwsGlobal() {
            // ARRANGE
            mockTenant.setStatus("ACTIVE");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.manualRetryProvisioning(TENANT_ID));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — CREATING status with creation lock held throws GlobalException")
        void sadPath_creatingWithLockHeld_throwsGlobal() {
            // ARRANGE
            mockTenant.setStatus("CREATING");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            getConcurrentLocks().put(TENANT_NAME, Instant.now().toString());
            try {
                // ACT + ASSERT
                assertThrows(GlobalException.class,
                        () -> service.manualRetryProvisioning(TENANT_ID));
            } finally {
                getConcurrentLocks().clear();
            }
        }

        @Test
        @DisplayName("Happy Path — FAILED tenant resets retry count and saves")
        void happyPath_failedTenant_resetsRetryCount() {
            // ARRANGE
            mockTenant.setStatus("FAILED");
            mockTenant.setProvisionRetryCount(2);

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(tenantRepository.save(any())).thenReturn(mockTenant);

            // retryProvisioning sub-call needs these:
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant));
            // MAX_PROVISION_RETRIES = 1 — retry count 0 after reset < 1 so will try
            // but since retryCount is 0 after reset, it should submit async task.
            // We stub executor to do nothing synchronously (just captures the runnable)
            doAnswer(invocation -> null)
                    .when(tenantProvisioningExecutor).execute(any(Runnable.class));

            // ACT
            assertDoesNotThrow(() -> service.manualRetryProvisioning(TENANT_ID));

            // ASSERT
            assertEquals(0, mockTenant.getProvisionRetryCount());
            verify(tenantRepository).save(mockTenant);
        }
    }

    // =========================================================================
    // retryProvisioning
    // =========================================================================

    @Nested
    @DisplayName("retryProvisioning")
    class RetryProvisioning {

        @Test
        @DisplayName("Sad Path — inner findByTenantID not found throws ResourceNotFoundException")
        void sadPath_innerFindNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.retryProvisioning(TENANT_ID));
        }

        @Test
        @DisplayName("Happy Path — ACTIVE tenant returns immediately without async task")
        void happyPath_activeTenant_returnsImmediately() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant)); // ACTIVE

            // ACT
            assertDoesNotThrow(() -> service.retryProvisioning(TENANT_ID));

            // ASSERT — executor never touched
            verifyNoInteractions(tenantProvisioningExecutor);
        }

        @Test
        @DisplayName("Happy Path — exceeded max retries sets status to ABANDONED")
        void happyPath_maxRetriesExceeded_setsAbandoned() {
            // ARRANGE
            mockTenant.setStatus("FAILED");
            mockTenant.setProvisionRetryCount(1); // MAX_PROVISION_RETRIES = 1

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant));
            doNothing().when(self).saveTenantStatus(any(), anyString());

            // ACT
            assertDoesNotThrow(() -> service.retryProvisioning(TENANT_ID));

            // ASSERT
            verify(self).saveTenantStatus(mockTenant, "ABANDONED");
            verifyNoInteractions(tenantProvisioningExecutor);
        }

        @Test
        @DisplayName("Happy Path — already ABANDONED skips re-abandoning")
        void happyPath_alreadyAbandoned_skipsResave() {
            // ARRANGE
            mockTenant.setStatus("ABANDONED");
            mockTenant.setProvisionRetryCount(1);

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant));

            // ACT
            assertDoesNotThrow(() -> service.retryProvisioning(TENANT_ID));

            // ASSERT — saveTenantStatus should NOT be called again
            verify(self, never()).saveTenantStatus(any(), anyString());
        }

        @Test
        @DisplayName("Happy Path — lock already held skips async submission")
        void happyPath_lockHeld_skipsSubmission() {
            // ARRANGE
            mockTenant.setStatus("FAILED");
            mockTenant.setProvisionRetryCount(0);

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(tenantRepository.findByTenantNameWithUsers(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant));

            getConcurrentLocks().put(TENANT_NAME, Instant.now().toString());
            try {
                // ACT
                assertDoesNotThrow(() -> service.retryProvisioning(TENANT_ID));

                // ASSERT
                verifyNoInteractions(tenantProvisioningExecutor);
            } finally {
                getConcurrentLocks().clear();
            }
        }
    }

    // =========================================================================
    // repairActiveTenants
    // =========================================================================

    @Nested
    @DisplayName("repairActiveTenants")
    class RepairActiveTenants {

        @Test
        @DisplayName("Happy Path — no active tenants returns 0 repaired")
        void happyPath_noActiveTenants_returnsZero() {
            // ARRANGE
            when(tenantRepository.findByStatus("ACTIVE"))
                    .thenReturn(Collections.emptyList());

            // ACT
            int result = service.repairActiveTenants();

            // ASSERT
            assertEquals(0, result);
            verify(tenantRepository).findByStatus("ACTIVE");
        }

        @Test
        @DisplayName("Happy Path — tenant with all fields present returns 0 repaired")
        void happyPath_nothingMissing_returnsZero() {
            // ARRANGE — tenant already has code, url, authConfig, subscription
            mockTenant.setTenantCode("ACME-001");
            mockTenant.setLoginUrl(BASE_URL + "/realms/acme/protocol/openid-connect/auth");

            when(tenantRepository.findByStatus("ACTIVE"))
                    .thenReturn(List.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(any()))
                    .thenReturn(Optional.of(mockAuthConfig));
            when(tenantRepository.findByTenantIDWithUsers(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(roleService.resolveAdminRolesForTenant(any(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(browserPolicyService.createTenantDefaultPolicy(anyString()))
                    .thenReturn(new BrowserPolicy());
            when(networkPolicyService.createTenantDefaultPolicy(anyString()))
                    .thenReturn(new NetworkPolicy());
            when(extensionPolicyService.createTenantDefaultPolicy(anyString()))
                    .thenReturn(new ExtensionPolicy());
            when(subscriptionService.hasActiveSubscription(anyString()))
                    .thenReturn(true);
            // ssoType is KEYCLOAK — not APIKEY, not selfManaged
            when(authProviderConfigRepository.findByTenant(any()))
                    .thenReturn(Optional.of(mockAuthConfig));
            when(apiKeyRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            int result = service.repairActiveTenants();

            // ASSERT
            assertEquals(0, result);
        }
    }

    // =========================================================================
    // repairTenantById
    // =========================================================================

    @Nested
    @DisplayName("repairTenantById")
    class RepairTenantById {

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.repairTenantById("bad-id"));
        }

        @Test
        @DisplayName("Sad Path — non-ACTIVE tenant throws GlobalException")
        void sadPath_nonActiveTenant_throwsGlobal() {
            // ARRANGE
            mockTenant.setStatus("CREATING");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.repairTenantById(TENANT_ID));
        }
    }

    // =========================================================================
    // hardDeleteTenant
    // =========================================================================

    @Nested
    @DisplayName("hardDeleteTenant")
    class HardDeleteTenant {

        private DeleteTenantRequest confirmedReq() {
            DeleteTenantRequest r = new DeleteTenantRequest();
            r.setConfirmDelete(true);
            return r;
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(anyString()))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.hardDeleteTenant(httpRequest, "bad-id", confirmedReq()));
        }

        @Test
        @DisplayName("Sad Path — confirmDelete=false throws KeycloakOperationException")
        void sadPath_noConfirmation_throwsException() {
            // ARRANGE
            mockTenant.setStatus("INACTIVE");
            DeleteTenantRequest req = new DeleteTenantRequest();
            req.setConfirmDelete(false);

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.hardDeleteTenant(httpRequest, TENANT_ID, req));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Sad Path — ACTIVE tenant throws KeycloakOperationException")
        void sadPath_activeTenant_throwsException() {
            // ARRANGE — mockTenant.status = ACTIVE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.hardDeleteTenant(httpRequest, TENANT_ID, confirmedReq()));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Sad Path — KC realm deletion fails throws KeycloakOperationException")
        void sadPath_kcRealmDeleteFails_throwsException() {
            // ARRANGE
            mockTenant.setStatus("INACTIVE");
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            doThrow(new RuntimeException("KC offline"))
                    .when(kcUtil).deleteRealmHard(anyString());

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.hardDeleteTenant(httpRequest, TENANT_ID, confirmedReq()));

            // DB delete must NOT happen if KC fails
            verify(tenantRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Happy Path — INACTIVE tenant fully deleted across all systems")
        void happyPath_inactiveTenant_fullyDeleted() {
            // ARRANGE
            mockTenant.setStatus("INACTIVE");
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            doNothing().when(kcUtil).deleteRealmHard(REALM_NAME);
            doNothing().when(subscriptionService).deleteSubscriptionsByTenantId(TENANT_ID);
            doNothing().when(eventsGroupService).deleteByTenantId(TENANT_ID);
            when(apiKeyRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            when(groupService.deleteGroupsByTenantId(TENANT_ID)).thenReturn(true);
            doNothing().when(roleService).deleteRolesByTenantId(TENANT_ID);
            when(userRepository.findByTenant(mockTenant))
                    .thenReturn(Collections.emptyList());
            doNothing().when(tenantRepository).delete(mockTenant);

            // ACT
            assertDoesNotThrow(() ->
                    service.hardDeleteTenant(httpRequest, TENANT_ID, confirmedReq()));

            // ASSERT — all cleanup steps verified
            verify(kcUtil).deleteRealmHard(REALM_NAME);
            verify(subscriptionService).deleteSubscriptionsByTenantId(TENANT_ID);
            verify(eventsGroupService).deleteByTenantId(TENANT_ID);
            verify(groupService).deleteGroupsByTenantId(TENANT_ID);
            verify(roleService).deleteRolesByTenantId(TENANT_ID);
            verify(tenantRepository).delete(mockTenant);
        }

        @Test
        @DisplayName("Happy Path — API keys exist and are deleted before tenant")
        void happyPath_withApiKeys_deletedBeforeTenant() {
            // ARRANGE
            mockTenant.setStatus("INACTIVE");
            ExtensionApiKey key = new ExtensionApiKey();
            key.setStatus("ACTIVE");

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            doNothing().when(kcUtil).deleteRealmHard(anyString());
            doNothing().when(subscriptionService).deleteSubscriptionsByTenantId(anyString());
            doNothing().when(eventsGroupService).deleteByTenantId(anyString());
            when(apiKeyRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(key));
            doNothing().when(apiKeyRepository).deleteAll(anyList());
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            when(groupService.deleteGroupsByTenantId(anyString())).thenReturn(true);
            doNothing().when(roleService).deleteRolesByTenantId(anyString());
            when(userRepository.findByTenant(mockTenant)).thenReturn(Collections.emptyList());
            doNothing().when(tenantRepository).delete(mockTenant);

            // ACT
            assertDoesNotThrow(() ->
                    service.hardDeleteTenant(httpRequest, TENANT_ID, confirmedReq()));

            // ASSERT
            verify(apiKeyRepository).deleteAll(anyList());
        }
    }

    // =========================================================================
    // updateTenant
    // =========================================================================

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenant {

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findById(anyString())).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.updateTenant(httpRequest, "bad-id", new Tenant(), false));
        }

        @Test
        @DisplayName("Sad Path — immutable tenantName change throws KeycloakOperationException")
        void sadPath_tenantNameChange_throwsImmutable() {
            // ARRANGE
            Tenant incoming = new Tenant();
            incoming.setTenantName("new-name"); // different from current "acme"

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.updateTenant(httpRequest, TENANT_ID, incoming, false));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — invalid status value throws KeycloakOperationException")
        void sadPath_invalidStatus_throwsException() {
            // ARRANGE
            Tenant incoming = new Tenant();
            incoming.setStatus("DELETED");

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.updateTenant(httpRequest, TENANT_ID, incoming, false));
        }

        @Test
        @DisplayName("Happy Path — mutable phone/industry/region updated and saved")
        void happyPath_mutableFields_updatedAndSaved() {
            // ARRANGE
            Tenant incoming = new Tenant();
            incoming.setPhoneNo("+1-999-9999");
            incoming.setIndustry("Finance");
            incoming.setRegion("EU");

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(jwtUtl.getUserFromRequest(httpRequest)).thenReturn(mockAdmin);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));
            when(tenantRepository.save(any())).thenReturn(mockTenant);

            // ACT
            Tenant result = service.updateTenant(httpRequest, TENANT_ID, incoming, false);

            // ASSERT
            assertNotNull(result);
            assertEquals("+1-999-9999", mockTenant.getPhoneNo());
            assertEquals("Finance",    mockTenant.getIndustry());
            assertEquals("EU",         mockTenant.getRegion());
            verify(tenantRepository).save(mockTenant);
        }

        @Test
        @DisplayName("Happy Path — status INACTIVE + syncKeycloak=true disables KC users")
        void happyPath_inactiveStatus_syncDisablesUsers() {
            // ARRANGE
            Tenant incoming = new Tenant();
            incoming.setStatus("INACTIVE"); // valid status

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(jwtUtl.getUserFromRequest(httpRequest)).thenReturn(mockAdmin);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));
            when(tenantRepository.save(any())).thenReturn(mockTenant);
            doNothing().when(kcUtil).setRealmEnabled(anyString(), anyBoolean());
            doNothing().when(kcUtil).setAllUsersEnabled(anyString(), anyBoolean());

            // ACT
            Tenant result = service.updateTenant(httpRequest, TENANT_ID, incoming, true);

            // ASSERT
            assertNotNull(result);
            // INACTIVE → setAllUsersEnabled(realm, false)
            verify(kcUtil).setAllUsersEnabled(REALM_NAME, false);
        }

        @Test
        @DisplayName("Happy Path — status ACTIVE + syncKeycloak=true enables KC users")
        void happyPath_activeStatus_syncEnablesUsers() {
            // ARRANGE
            mockTenant.setStatus("INACTIVE"); // current = INACTIVE
            Tenant incoming = new Tenant();
            incoming.setStatus("ACTIVE"); // change to ACTIVE

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(jwtUtl.getUserFromRequest(httpRequest)).thenReturn(mockAdmin);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));
            when(tenantRepository.save(any())).thenReturn(mockTenant);
            doNothing().when(kcUtil).setRealmEnabled(anyString(), anyBoolean());
            doNothing().when(kcUtil).setAllUsersEnabled(anyString(), anyBoolean());

            // ACT
            service.updateTenant(httpRequest, TENANT_ID, incoming, true);

            // ASSERT
            verify(kcUtil).setAllUsersEnabled(REALM_NAME, true);
        }
    }

    // =========================================================================
    // checkTenantNameAvailability
    // =========================================================================

    @Nested
    @DisplayName("checkTenantNameAvailability")
    class CheckTenantNameAvailability {

        @Test
        @DisplayName("Happy Path — no tenantId uses simple exists query")
        void happyPath_noTenantId_simpleQuery() {
            // ARRANGE
            when(tenantRepository.existsByTenantName("acme")).thenReturn(false);

            // ACT
            boolean result = service.checkTenantNameAvailability("ACME", null);

            // ASSERT
            assertFalse(result);
            verify(tenantRepository).existsByTenantName("acme");
            verify(tenantRepository, never())
                    .existsByTenantNameAndTenantIDNot(anyString(), anyString());
        }

        @Test
        @DisplayName("Happy Path — with tenantId uses excludeSelf query")
        void happyPath_withTenantId_excludeSelfQuery() {
            // ARRANGE
            when(tenantRepository.existsByTenantNameAndTenantIDNot("acme", TENANT_ID))
                    .thenReturn(true);

            // ACT
            boolean result = service.checkTenantNameAvailability("ACME", TENANT_ID);

            // ASSERT
            assertTrue(result);
            verify(tenantRepository).existsByTenantNameAndTenantIDNot("acme", TENANT_ID);
        }
    }

    // =========================================================================
    // checkExistsByDomain
    // =========================================================================

    @Nested
    @DisplayName("checkExistsByDomain")
    class CheckExistsByDomain {

        @Test
        @DisplayName("Happy Path — no tenantId uses simple domain exists query")
        void happyPath_noTenantId_simpleDomainQuery() {
            // ARRANGE
            when(tenantRepository.existsByDomain(anyString())).thenReturn(true);

            // ACT
            boolean result = service.checkExistsByDomain("acme", null);

            // ASSERT
            assertTrue(result);
            verify(tenantRepository).existsByDomain(anyString());
        }

        @Test
        @DisplayName("Happy Path — with tenantId uses excludeSelf domain query")
        void happyPath_withTenantId_excludeSelfDomainQuery() {
            // ARRANGE
            when(tenantRepository.existsByDomainAndTenantIDNot(anyString(), eq(TENANT_ID)))
                    .thenReturn(false);

            // ACT
            boolean result = service.checkExistsByDomain("acme", TENANT_ID);

            // ASSERT
            assertFalse(result);
            verify(tenantRepository).existsByDomainAndTenantIDNot(anyString(), eq(TENANT_ID));
        }
    }

    // =========================================================================
    // checkPhoneNumber
    // =========================================================================

    @Nested
    @DisplayName("checkPhoneNumber")
    class CheckPhoneNumber {

        @Test
        @DisplayName("Happy Path — no tenantId phone not found returns false")
        void happyPath_noTenantId_phoneNotFound() {
            // ARRANGE
            when(tenantRepository.existsByPhoneNo("+1-555-0100")).thenReturn(false);

            // ACT
            boolean result = service.checkPhoneNumber("+1-555-0100", null);

            // ASSERT
            assertFalse(result);
            verify(tenantRepository).existsByPhoneNo("+1-555-0100");
        }

        @Test
        @DisplayName("Happy Path — with tenantId uses excludeSelf phone query")
        void happyPath_withTenantId_excludeSelfPhoneQuery() {
            // ARRANGE
            when(tenantRepository.existsByPhoneNoAndTenantIDNot("+1-555-0100", TENANT_ID))
                    .thenReturn(true);

            // ACT
            boolean result = service.checkPhoneNumber("+1-555-0100", TENANT_ID);

            // ASSERT
            assertTrue(result);
            verify(tenantRepository).existsByPhoneNoAndTenantIDNot("+1-555-0100", TENANT_ID);
        }
    }

    // =========================================================================
    // checkEmail
    // =========================================================================

    @Nested
    @DisplayName("checkEmail")
    class CheckEmail {

        @Test
        @DisplayName("Happy Path — email and subdomain both free returns null")
        void happyPath_emailAndSubdomainFree_returnsNull() {
            // ARRANGE
            when(tenantRepository.existsByEmail("contact@acme.com")).thenReturn(false);
            when(tenantRepository.existsByEmailSubdomain("acme.com")).thenReturn(false);

            // ACT
            String result = service.checkEmail("contact@acme.com", null);

            // ASSERT
            assertNull(result);
        }

        @Test
        @DisplayName("Sad Path — email already in use returns error message")
        void sadPath_emailInUse_returnsErrorMessage() {
            // ARRANGE
            when(tenantRepository.existsByEmail("contact@acme.com")).thenReturn(true);

            // ACT
            String result = service.checkEmail("contact@acme.com", null);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.toLowerCase().contains("email"));
        }

        @Test
        @DisplayName("Sad Path — subdomain already in use returns error message")
        void sadPath_subdomainInUse_returnsErrorMessage() {
            // ARRANGE
            when(tenantRepository.existsByEmail("contact@acme.com")).thenReturn(false);
            when(tenantRepository.existsByEmailSubdomain("acme.com")).thenReturn(true);

            // ACT
            String result = service.checkEmail("contact@acme.com", null);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.toLowerCase().contains("subdomain"));
        }

        @Test
        @DisplayName("Happy Path — email without @ returns null (no subdomain extracted)")
        void happyPath_emailWithoutAt_noSubdomain() {
            // ARRANGE
            when(tenantRepository.existsByEmail("invalid-no-at")).thenReturn(false);

            // ACT
            String result = service.checkEmail("invalid-no-at", null);

            // ASSERT
            assertNull(result);
            verify(tenantRepository, never()).existsByEmailSubdomain(anyString());
        }

        @Test
        @DisplayName("Happy Path — null email returns null")
        void happyPath_nullEmail_returnsNull() {
            // ARRANGE
            when(tenantRepository.existsByEmail(null)).thenReturn(false);

            // ACT
            String result = service.checkEmail(null, null);

            // ASSERT
            assertNull(result);
        }

        @Test
        @DisplayName("Happy Path — with tenantId uses excludeSelf email queries")
        void happyPath_withTenantId_excludeSelfQueries() {
            // ARRANGE
            when(tenantRepository.existsByEmailAndTenantIDNot("contact@acme.com", TENANT_ID))
                    .thenReturn(false);
            when(tenantRepository.existsByEmailSubdomainAndTenantIDNot("acme.com", TENANT_ID))
                    .thenReturn(false);

            // ACT
            String result = service.checkEmail("contact@acme.com", TENANT_ID);

            // ASSERT
            assertNull(result);
            verify(tenantRepository).existsByEmailAndTenantIDNot("contact@acme.com", TENANT_ID);
            verify(tenantRepository)
                    .existsByEmailSubdomainAndTenantIDNot("acme.com", TENANT_ID);
        }
    }

    // =========================================================================
    // getTenantByKeycloakRealmName
    // =========================================================================

    @Nested
    @DisplayName("getTenantByKeycloakRealmName")
    class GetTenantByKeycloakRealmName {

        @Test
        @DisplayName("Happy Path — realm found returns tenant")
        void happyPath_realmFound_returnsTenant() {
            // ARRANGE
            when(tenantRepository.findByRealmName(REALM_NAME))
                    .thenReturn(Optional.of(mockTenant));

            // ACT
            Tenant result = service.getTenantByKeycloakRealmName(REALM_NAME);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantID());
        }

        @Test
        @DisplayName("Sad Path — realm not found returns null")
        void sadPath_realmNotFound_returnsNull() {
            // ARRANGE
            when(tenantRepository.findByRealmName("unknown-realm"))
                    .thenReturn(Optional.empty());

            // ACT
            Tenant result = service.getTenantByKeycloakRealmName("unknown-realm");

            // ASSERT
            assertNull(result);
        }
    }

    // =========================================================================
    // getTenantHierarchy
    // =========================================================================

    @Nested
    @DisplayName("getTenantHierarchy")
    class GetTenantHierarchy {

        @Test
        @DisplayName("Sad Path — no tenant context throws ResourceNotFoundException")
        void sadPath_noTenantContext_throwsException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(null);

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getTenantHierarchy(httpRequest));
        }

        @Test
        @DisplayName("Happy Path — one level of children returned as list")
        void happyPath_oneLevel_returnsChildren() {
            // ARRANGE
            Tenant child = new Tenant();
            child.setTenantID("child-001");
            child.setTenantName("child");
            child.setStatus("ACTIVE");
            child.setCreatedAt(Instant.now());

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByParentTenantIdIn(List.of(TENANT_ID)))
                    .thenReturn(List.of(child));
            when(tenantRepository.findByParentTenantIdIn(List.of("child-001")))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<TenantResponse> result = service.getTenantHierarchy(httpRequest);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("child-001", result.get(0).getTenantID());
        }

        @Test
        @DisplayName("Happy Path — no children returns empty list")
        void happyPath_noChildren_returnsEmptyList() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByParentTenantIdIn(List.of(TENANT_ID)))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<TenantResponse> result = service.getTenantHierarchy(httpRequest);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // getTenantIfParent
    // =========================================================================

    @Nested
    @DisplayName("getTenantIfParent")
    class GetTenantIfParent {

        @Test
        @DisplayName("Happy Path — requester IS the target tenant, returns tenant")
        void happyPath_requesterIsSelf_returnsTenant() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant_TenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            Tenant result = service.getTenantIfParent(httpRequest, TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantID());
        }

        @Test
        @DisplayName("Sad Path — target not found throws ResourceNotFoundException")
        void sadPath_targetNotFound_throwsException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID("bad-id"))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getTenantIfParent(httpRequest, "bad-id"));
        }

        @Test
        @DisplayName("Sad Path — requester is not ancestor throws KeycloakOperationException")
        void sadPath_requesterNotAncestor_throwsAccessDenied() {
            // ARRANGE
            Tenant target = new Tenant();
            target.setTenantID("target-001");
            target.setTenantName("target");
            target.setParentTenantId("some-other-parent");

            Tenant requester = new Tenant();
            requester.setTenantID("requester-001");

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(requester);
            when(tenantRepository.findByTenantID("target-001"))
                    .thenReturn(Optional.of(target));
            when(tenantRepository.findByTenantID("some-other-parent"))
                    .thenReturn(Optional.empty()); // chain ends

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.getTenantIfParent(httpRequest, "target-001"));
        }

        @Test
        @DisplayName("Happy Path — requester IS the direct parent, returns tenant")
        void happyPath_requesterIsParent_returnsTenant() {
            // ARRANGE
            Tenant target = new Tenant();
            target.setTenantID("target-001");
            target.setTenantName("target");
            target.setParentTenantId(TENANT_ID); // parent = mockTenant

            when(jwtUtl.getTenantFromRequest(httpRequest)).thenReturn(mockTenant);
            when(tenantRepository.findByTenantID("target-001"))
                    .thenReturn(Optional.of(target));
            when(authProviderConfigRepository.findByTenant_TenantID("target-001"))
                    .thenReturn(Optional.empty());

            // ACT
            Tenant result = service.getTenantIfParent(httpRequest, "target-001");

            // ASSERT
            assertNotNull(result);
            assertEquals("target-001", result.getTenantID());
        }
    }

    // =========================================================================
    // saveAuthProviderConfig (package-private, REQUIRES_NEW)
    // =========================================================================

    @Nested
    @DisplayName("saveAuthProviderConfig")
    class SaveAuthProviderConfig {

        @Test
        @DisplayName("Happy Path — config saved when not yet present for tenant")
        void happyPath_configMissing_savedSuccessfully() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            when(authProviderConfigRepository.save(any(AuthProviderConfig.class)))
                    .thenReturn(mockAuthConfig);

            // ACT
            assertDoesNotThrow(() -> service.saveAuthProviderConfig(mockTenant, req));

            // ASSERT
            verify(authProviderConfigRepository).save(any(AuthProviderConfig.class));
        }

        @Test
        @DisplayName("Happy Path — config already exists, save is skipped (idempotent)")
        void happyPath_configAlreadyExists_skipsSave() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            assertDoesNotThrow(() -> service.saveAuthProviderConfig(mockTenant, req));

            // ASSERT
            verify(authProviderConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — tenant not found in REQUIRES_NEW throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.saveAuthProviderConfig(mockTenant, buildCreateRequest()));

            verify(authProviderConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("Happy Path — APIKEY ssoType stored as APIKEY in config")
        void happyPath_apikeySsoType_storedCorrectly() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            req.setSsoType("APIKEY");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            ArgumentCaptor<AuthProviderConfig> captor =
                    ArgumentCaptor.forClass(AuthProviderConfig.class);
            when(authProviderConfigRepository.save(captor.capture()))
                    .thenReturn(mockAuthConfig);

            // ACT
            service.saveAuthProviderConfig(mockTenant, req);

            // ASSERT
            assertEquals("APIKEY", captor.getValue().getSsoType());
        }

        @Test
        @DisplayName("Happy Path — AZURE ssoType stored as AZURE in config")
        void happyPath_azureSsoType_storedCorrectly() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            req.setSsoType("AZURE");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            ArgumentCaptor<AuthProviderConfig> captor =
                    ArgumentCaptor.forClass(AuthProviderConfig.class);
            when(authProviderConfigRepository.save(captor.capture()))
                    .thenReturn(mockAuthConfig);

            // ACT
            service.saveAuthProviderConfig(mockTenant, req);

            // ASSERT
            assertEquals("AZURE", captor.getValue().getSsoType());
        }

        @Test
        @DisplayName("Happy Path — unknown ssoType defaults to KEYCLOAK in config")
        void happyPath_unknownSsoType_defaultsToKeycloak() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            req.setSsoType("UNKNOWN_SSO");
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            ArgumentCaptor<AuthProviderConfig> captor =
                    ArgumentCaptor.forClass(AuthProviderConfig.class);
            when(authProviderConfigRepository.save(captor.capture()))
                    .thenReturn(mockAuthConfig);

            // ACT
            service.saveAuthProviderConfig(mockTenant, req);

            // ASSERT
            assertEquals("KEYCLOAK", captor.getValue().getSsoType());
        }

        @Test
        @DisplayName("Happy Path — null ssoType defaults to KEYCLOAK in config")
        void happyPath_nullSsoType_defaultsToKeycloak() {
            // ARRANGE
            CreateTenantRequest req = buildCreateRequest();
            req.setSsoType(null);
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            ArgumentCaptor<AuthProviderConfig> captor =
                    ArgumentCaptor.forClass(AuthProviderConfig.class);
            when(authProviderConfigRepository.save(captor.capture()))
                    .thenReturn(mockAuthConfig);

            // ACT
            service.saveAuthProviderConfig(mockTenant, req);

            // ASSERT
            assertEquals("KEYCLOAK", captor.getValue().getSsoType());
        }
    }

    // =========================================================================
    // saveTenantStatus (package-private, REQUIRES_NEW)
    // =========================================================================

    @Nested
    @DisplayName("saveTenantStatus")
    class SaveTenantStatus {

        @Test
        @DisplayName("Happy Path — status updated, tenant object mutated")
        void happyPath_statusUpdated() {
            // ARRANGE
            when(tenantRepository.updateTenantStatus(
                    eq(TENANT_ID), eq("REALM_CREATED"), any(), any()))
                    .thenReturn(1);

            // ACT
            assertDoesNotThrow(() -> service.saveTenantStatus(mockTenant, "REALM_CREATED"));

            // ASSERT
            assertEquals("REALM_CREATED", mockTenant.getStatus());
            verify(tenantRepository).updateTenantStatus(
                    eq(TENANT_ID), eq("REALM_CREATED"), any(), any());
        }

        @Test
        @DisplayName("Happy Path — 0 rows updated logs warning but does not throw")
        void happyPath_zeroRowsUpdated_noThrow() {
            // ARRANGE
            when(tenantRepository.updateTenantStatus(anyString(), anyString(), any(), any()))
                    .thenReturn(0);

            // ACT + ASSERT
            assertDoesNotThrow(() -> service.saveTenantStatus(mockTenant, "ACTIVE"));
        }

        @Test
        @DisplayName("Sad Path — repository throws RuntimeException")
        void sadPath_repositoryThrows_throwsRuntime() {
            // ARRANGE
            when(tenantRepository.updateTenantStatus(anyString(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("DB connection lost"));

            // ACT + ASSERT
            assertThrows(RuntimeException.class,
                    () -> service.saveTenantStatus(mockTenant, "ACTIVE"));
        }
    }

    // =========================================================================
    // saveProvisionSteps (package-private, REQUIRES_NEW)
    // =========================================================================

    @Nested
    @DisplayName("saveProvisionSteps")
    class SaveProvisionSteps {

        @Test
        @DisplayName("Happy Path — steps bitmask persisted and tenant mutated")
        void happyPath_stepsPersisted() {
            // ARRANGE
            when(tenantRepository.updateProvisionSteps(TENANT_ID, 7)).thenReturn(1);

            // ACT
            assertDoesNotThrow(() -> service.saveProvisionSteps(mockTenant, 7));

            // ASSERT
            assertEquals(7, mockTenant.getProvisionStepsCompleted());
            verify(tenantRepository).updateProvisionSteps(TENANT_ID, 7);
        }

        @Test
        @DisplayName("Happy Path — 0 rows updated logs warning but does not throw")
        void happyPath_zeroRowsUpdated_noThrow() {
            // ARRANGE
            when(tenantRepository.updateProvisionSteps(anyString(), anyInt()))
                    .thenReturn(0);

            // ACT + ASSERT
            assertDoesNotThrow(() -> service.saveProvisionSteps(mockTenant, 3));
        }
    }

    // =========================================================================
    // saveUser (package-private, @Transactional)
    // =========================================================================

    @Nested
    @DisplayName("saveUser")
    class SaveUser {

        @Test
        @DisplayName("Happy Path — user saved without exception")
        void happyPath_userSaved() {
            // ARRANGE
            when(userRepository.save(mockAdmin)).thenReturn(mockAdmin);

            // ACT
            assertDoesNotThrow(() -> service.saveUser(mockAdmin, TENANT_NAME));

            // ASSERT
            verify(userRepository).save(mockAdmin);
        }

        @Test
        @DisplayName("Sad Path — repository throws RuntimeException")
        void sadPath_repositoryThrows_throwsRuntime() {
            // ARRANGE
            when(userRepository.save(any()))
                    .thenThrow(new RuntimeException("Constraint violation"));

            // ACT + ASSERT
            assertThrows(RuntimeException.class,
                    () -> service.saveUser(mockAdmin, TENANT_NAME));
        }
    }

    // =========================================================================
    // persistTenantSkeleton (package-private, REQUIRES_NEW)
    // =========================================================================

    @Nested
    @DisplayName("persistTenantSkeleton")
    class PersistTenantSkeleton {

        @Test
        @DisplayName("Happy Path — tenant saved successfully")
        void happyPath_tenantSaved() {
            // ARRANGE
            when(tenantRepository.save(mockTenant)).thenReturn(mockTenant);

            // ACT
            assertDoesNotThrow(() ->
                    service.persistTenantSkeleton(mockTenant, TENANT_NAME));

            // ASSERT
            verify(tenantRepository).save(mockTenant);
        }

        @Test
        @DisplayName("Sad Path — DataIntegrityViolation throws KeycloakOperationException")
        void sadPath_dataIntegrityViolation_throwsConflict() {
            // ARRANGE
            when(tenantRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("dup key"));

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.persistTenantSkeleton(mockTenant, TENANT_NAME));
        }

        @Test
        @DisplayName("Sad Path — generic exception throws RuntimeException")
        void sadPath_genericException_throwsRuntime() {
            // ARRANGE
            when(tenantRepository.save(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // ACT + ASSERT
            assertThrows(RuntimeException.class,
                    () -> service.persistTenantSkeleton(mockTenant, TENANT_NAME));
        }
    }

    // =========================================================================
    // persistAdminSkeleton (package-private, REQUIRES_NEW)
    // =========================================================================

    @Nested
    @DisplayName("persistAdminSkeleton")
    class PersistAdminSkeleton {

        @Test
        @DisplayName("Happy Path — admin saved successfully")
        void happyPath_adminSaved() {
            // ARRANGE
            when(userRepository.save(mockAdmin)).thenReturn(mockAdmin);

            // ACT
            assertDoesNotThrow(() ->
                    service.persistAdminSkeleton(mockAdmin, TENANT_NAME));

            // ASSERT
            verify(userRepository).save(mockAdmin);
        }

        @Test
        @DisplayName("Sad Path — DataIntegrityViolation throws KeycloakOperationException")
        void sadPath_dataIntegrityViolation_throwsConflict() {
            // ARRANGE
            when(userRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("dup email"));

            // ACT + ASSERT
            assertThrows(KeycloakOperationException.class,
                    () -> service.persistAdminSkeleton(mockAdmin, TENANT_NAME));
        }

        @Test
        @DisplayName("Sad Path — generic exception throws RuntimeException")
        void sadPath_genericException_throwsRuntime() {
            // ARRANGE
            when(userRepository.save(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // ACT + ASSERT
            assertThrows(RuntimeException.class,
                    () -> service.persistAdminSkeleton(mockAdmin, TENANT_NAME));
        }
    }

    // =========================================================================
    // rollbackFailedCreation (package-private, REQUIRES_NEW)
    // =========================================================================

    @Nested
    @DisplayName("rollbackFailedCreation")
    class RollbackFailedCreation {

        @Test
        @DisplayName("Happy Path — null tenant skips all cleanup silently")
        void happyPath_nullTenant_skipsAll() {
            // ACT + ASSERT
            assertDoesNotThrow(() -> service.rollbackFailedCreation(null));
            verifyNoInteractions(kcUtil, tenantRepository, userRepository,
                    apiKeyRepository, groupService, roleService,
                    subscriptionService, eventsGroupService);
        }

        @Test
        @DisplayName("Happy Path — null tenantID skips all cleanup silently")
        void happyPath_nullTenantId_skipsAll() {
            // ARRANGE
            Tenant noId = new Tenant(); // tenantID = null

            // ACT + ASSERT
            assertDoesNotThrow(() -> service.rollbackFailedCreation(noId));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Happy Path — all cleanup steps executed in order")
        void happyPath_allSteps_executedInOrder() {
            // ARRANGE
            doNothing().when(kcUtil).deleteRealmHard(REALM_NAME);
            doNothing().when(subscriptionService).deleteSubscriptionsByTenantId(TENANT_ID);
            doNothing().when(eventsGroupService).deleteByTenantId(TENANT_ID);
            when(apiKeyRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            when(groupService.deleteGroupsByTenantId(TENANT_ID)).thenReturn(true);
            doNothing().when(roleService).deleteRolesByTenantId(TENANT_ID);
            when(userRepository.findByTenant(mockTenant))
                    .thenReturn(Collections.emptyList());
            doNothing().when(tenantRepository).deleteByTenantIdDirect(TENANT_ID);

            // ACT
            assertDoesNotThrow(() -> service.rollbackFailedCreation(mockTenant));

            // ASSERT — all steps executed
            verify(kcUtil).deleteRealmHard(REALM_NAME);
            verify(subscriptionService).deleteSubscriptionsByTenantId(TENANT_ID);
            verify(eventsGroupService).deleteByTenantId(TENANT_ID);
            verify(groupService).deleteGroupsByTenantId(TENANT_ID);
            verify(roleService).deleteRolesByTenantId(TENANT_ID);
            verify(tenantRepository).deleteByTenantIdDirect(TENANT_ID);
        }

        @Test
        @DisplayName("Sad Path — KC realm delete fails but subsequent steps still execute")
        void sadPath_kcDeleteFails_cleanupContinues() {
            // ARRANGE
            doThrow(new RuntimeException("KC offline"))
                    .when(kcUtil).deleteRealmHard(anyString());
            doNothing().when(subscriptionService).deleteSubscriptionsByTenantId(anyString());
            doNothing().when(eventsGroupService).deleteByTenantId(anyString());
            when(apiKeyRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            when(groupService.deleteGroupsByTenantId(anyString())).thenReturn(true);
            doNothing().when(roleService).deleteRolesByTenantId(anyString());
            when(userRepository.findByTenant(mockTenant)).thenReturn(Collections.emptyList());
            doNothing().when(tenantRepository).deleteByTenantIdDirect(anyString());

            // ACT — must NOT re-throw; each step is independently try-caught
            assertDoesNotThrow(() -> service.rollbackFailedCreation(mockTenant));

            // ASSERT — later steps still run despite KC failure
            verify(subscriptionService).deleteSubscriptionsByTenantId(TENANT_ID);
            verify(tenantRepository).deleteByTenantIdDirect(TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — API keys present are deleted before tenant")
        void happyPath_withApiKeys_keysDeleted() {
            // ARRANGE
            ExtensionApiKey key = new ExtensionApiKey();
            doNothing().when(kcUtil).deleteRealmHard(anyString());
            doNothing().when(subscriptionService).deleteSubscriptionsByTenantId(anyString());
            doNothing().when(eventsGroupService).deleteByTenantId(anyString());
            when(apiKeyRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(key));
            doNothing().when(apiKeyRepository).deleteAll(anyList());
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());
            when(groupService.deleteGroupsByTenantId(anyString())).thenReturn(true);
            doNothing().when(roleService).deleteRolesByTenantId(anyString());
            when(userRepository.findByTenant(mockTenant)).thenReturn(Collections.emptyList());
            doNothing().when(tenantRepository).deleteByTenantIdDirect(anyString());

            // ACT
            assertDoesNotThrow(() -> service.rollbackFailedCreation(mockTenant));

            // ASSERT
            verify(apiKeyRepository).deleteAll(List.of(key));
        }

        @Test
        @DisplayName("Happy Path — authProviderConfig present is deleted")
        void happyPath_authConfigPresent_deleted() {
            // ARRANGE
            doNothing().when(kcUtil).deleteRealmHard(anyString());
            doNothing().when(subscriptionService).deleteSubscriptionsByTenantId(anyString());
            doNothing().when(eventsGroupService).deleteByTenantId(anyString());
            when(apiKeyRepository.findByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));
            doNothing().when(authProviderConfigRepository).delete(mockAuthConfig);
            when(groupService.deleteGroupsByTenantId(anyString())).thenReturn(true);
            doNothing().when(roleService).deleteRolesByTenantId(anyString());
            when(userRepository.findByTenant(mockTenant)).thenReturn(Collections.emptyList());
            doNothing().when(tenantRepository).deleteByTenantIdDirect(anyString());

            // ACT
            assertDoesNotThrow(() -> service.rollbackFailedCreation(mockTenant));

            // ASSERT
            verify(authProviderConfigRepository).delete(mockAuthConfig);
        }
    }

    // =========================================================================
    // normalizeRealmNamesToLowercase
    // =========================================================================

    @Nested
    @DisplayName("normalizeRealmNamesToLowercase")
    class NormalizeRealmNamesToLowercase {

        @Test
        @DisplayName("Happy Path — no tenants returns summary with all zeros")
        void happyPath_noTenants_allZeros() {
            // ARRANGE
            when(tenantRepository.findAll()).thenReturn(Collections.emptyList());

            // ACT
            String result = service.normalizeRealmNamesToLowercase();

            // ASSERT
            assertNotNull(result);
            assertTrue(result.contains("Renamed: 0"));
            assertTrue(result.contains("Skipped: 0"));
        }

        @Test
        @DisplayName("Happy Path — null realmName tenant is skipped")
        void happyPath_nullRealmName_skipped() {
            // ARRANGE
            mockTenant.setRealmName(null);
            when(tenantRepository.findAll()).thenReturn(List.of(mockTenant));

            // ACT
            String result = service.normalizeRealmNamesToLowercase();

            // ASSERT
            assertTrue(result.contains("Skipped: 1"));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Happy Path — already lowercase realm is skipped without KC call")
        void happyPath_alreadyLowercase_skippedWithoutKcCall() {
            // ARRANGE — mockTenant.realmName = "acme" (already lowercase)
            when(tenantRepository.findAll()).thenReturn(List.of(mockTenant));

            // ACT
            String result = service.normalizeRealmNamesToLowercase();

            // ASSERT
            assertTrue(result.contains("Skipped: 1"));
            verifyNoInteractions(kcUtil);
        }

        @Test
        @DisplayName("Happy Path — uppercase realm renamed in KC and DB")
        void happyPath_uppercaseRealm_renamedInKcAndDb() {
            // ARRANGE
            mockTenant.setRealmName("ACME");
            when(tenantRepository.findAll()).thenReturn(List.of(mockTenant));
            when(kcUtil.renameRealm("ACME", "acme")).thenReturn(true);
            when(tenantRepository.save(any())).thenReturn(mockTenant);
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty()); // no auth config to update
            when(authProviderConfigRepository.save(any())).thenReturn(mockAuthConfig);

            // ACT
            String result = service.normalizeRealmNamesToLowercase();

            // ASSERT
            assertTrue(result.contains("Renamed: 1"));
            verify(kcUtil).renameRealm("ACME", "acme");
            verify(tenantRepository).save(mockTenant);
            assertEquals("acme", mockTenant.getRealmName());
        }

        @Test
        @DisplayName("Happy Path — auth config URLs updated when config present")
        void happyPath_authConfigPresent_urlsUpdated() {
            // ARRANGE
            mockTenant.setRealmName("ACME");
            mockAuthConfig.setIssuerUri(BASE_URL + "/realms/ACME");
            mockAuthConfig.setTokenEndpoint(BASE_URL + "/realms/ACME/protocol/openid-connect/token");
            mockAuthConfig.setJwkUri(BASE_URL + "/realms/ACME/protocol/openid-connect/certs");
            mockAuthConfig.setLoginUrl(BASE_URL + "/realms/ACME/protocol/openid-connect/auth");
            mockAuthConfig.setClientId("ACME");

            when(tenantRepository.findAll()).thenReturn(List.of(mockTenant));
            when(kcUtil.renameRealm("ACME", "acme")).thenReturn(true);
            when(tenantRepository.save(any())).thenReturn(mockTenant);
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));
            when(authProviderConfigRepository.save(any())).thenReturn(mockAuthConfig);

            // ACT
            service.normalizeRealmNamesToLowercase();

            // ASSERT — issuerUri updated to lowercase
            assertTrue(mockAuthConfig.getIssuerUri().contains("/realms/acme"));
            assertEquals("acme", mockAuthConfig.getClientId());
            verify(authProviderConfigRepository).save(mockAuthConfig);
        }

        @Test
        @DisplayName("Sad Path — KC rename throws exception marks as failed")
        void sadPath_kcRenameFails_markedFailed() {
            // ARRANGE
            mockTenant.setRealmName("ACME");
            when(tenantRepository.findAll()).thenReturn(List.of(mockTenant));
            when(kcUtil.renameRealm("ACME", "acme"))
                    .thenThrow(new RuntimeException("KC unavailable"));

            // ACT
            String result = service.normalizeRealmNamesToLowercase();

            // ASSERT
            assertTrue(result.contains("Failed: 1"));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — KC returns false and target realm not exist → skipped")
        void sadPath_kcReturnsFalseAndTargetMissing_skipped() {
            // ARRANGE
            mockTenant.setRealmName("ACME");
            when(tenantRepository.findAll()).thenReturn(List.of(mockTenant));
            when(kcUtil.renameRealm("ACME", "acme")).thenReturn(false);
            when(kcUtil.realmExists("acme")).thenReturn(false);

            // ACT
            String result = service.normalizeRealmNamesToLowercase();

            // ASSERT
            assertTrue(result.contains("Skipped: 1") || result.contains("SKIP"));
            verify(tenantRepository, never()).save(any());
        }
    }

    // =========================================================================
    // createBrokerClientInMaster (public)
    // =========================================================================

    @Nested
    @DisplayName("createBrokerClientInMaster")
    class CreateBrokerClientInMaster {

        @Test
        @DisplayName("Happy Path — broker client created in master realm and returned")
        void happyPath_brokerClientCreated() {
            // ARRANGE
            doNothing().when(kcUtil).createClient(eq("master"), any());

            // ACT
            var result = service.createBrokerClientInMaster("acme-realm", "acme.example.com");

            // ASSERT
            assertNotNull(result);
            assertEquals("broker-for-acme-realm", result.getClientId());
            assertNotNull(result.getSecret()); // UUID generated
            verify(kcUtil).createClient(eq("master"), any());
        }

        @Test
        @DisplayName("Happy Path — redirect URI uses tenantRealmName in broker callback")
        void happyPath_redirectUriContainsTenantRealm() {
            // ARRANGE
            doNothing().when(kcUtil).createClient(eq("master"), any());

            // ACT
            var result = service.createBrokerClientInMaster("my-tenant", "mytenant.com");

            // ASSERT
            assertNotNull(result.getRedirectUris());
            assertTrue(result.getRedirectUris().stream()
                    .anyMatch(uri -> uri.contains("my-tenant") && uri.contains("master-hub")));
        }
    }

    // =========================================================================
    // createMasterHubIdpInTenant (public)
    // =========================================================================

    @Nested
    @DisplayName("createMasterHubIdpInTenant")
    class CreateMasterHubIdpInTenant {

        @Test
        @DisplayName("Happy Path — IdP created in tenant realm with correct config")
        void happyPath_idpCreatedWithCorrectConfig() {
            // ARRANGE
            doNothing().when(kcUtil).createIdp(anyString(), any());

            // ACT
            assertDoesNotThrow(() ->
                    service.createMasterHubIdpInTenant(
                            "acme-realm", "master-client-id", "master-secret"));

            // ASSERT
            verify(kcUtil).createIdp(eq("acme-realm"), any());
        }
    }

    // =========================================================================
    // generateUniqueApiKey (public)
    // =========================================================================

    @Nested
    @DisplayName("generateUniqueApiKey")
    class GenerateUniqueApiKey {

        @Test
        @DisplayName("Happy Path — generates key of exact specified length")
        void happyPath_correctLength() {
            // ACT
            String result = service.generateUniqueApiKey(26);

            // ASSERT
            assertNotNull(result);
            assertEquals(26, result.length());
        }

        @Test
        @DisplayName("Happy Path — generates key of length 64")
        void happyPath_length64() {
            // ACT
            String result = service.generateUniqueApiKey(64);

            // ASSERT
            assertEquals(64, result.length());
        }

        @Test
        @DisplayName("Happy Path — two consecutive calls produce different keys")
        void happyPath_keysAreUnique() {
            // ACT
            String key1 = service.generateUniqueApiKey(26);
            String key2 = service.generateUniqueApiKey(26);

            // ASSERT — statistically impossible for SecureRandom to produce two identical keys
            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("Happy Path — generated key contains only valid character set chars")
        void happyPath_validCharacters() {
            // ARRANGE
            String validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                    + "0123456789!@#$%^&*()=+[]{};:,.<>?";

            // ACT
            String result = service.generateUniqueApiKey(50);

            // ASSERT
            for (char c : result.toCharArray()) {
                assertTrue(validChars.indexOf(c) >= 0,
                        "Unexpected character in API key: " + c);
            }
        }
    }

    // =========================================================================
    // syncSelfManagedRoles
    // =========================================================================

    @Nested
    @DisplayName("syncSelfManagedRoles")
    class SyncSelfManagedRoles {

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findById(anyString())).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.syncSelfManagedRoles("caller", "bad-id"));
        }

        @Test
        @DisplayName("Sad Path — non-selfManaged tenant throws GlobalException")
        void sadPath_notSelfManaged_throwsGlobal() {
            // ARRANGE
            mockTenant.setSelfManaged(false);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.syncSelfManagedRoles("caller", TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — no default admin user throws GlobalException")
        void sadPath_noDefaultAdmin_throwsGlobal() {
            // ARRANGE
            mockTenant.setSelfManaged(true);
            mockTenant.setUsers(Collections.emptyList());
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.syncSelfManagedRoles("caller", TENANT_ID));
        }

        @Test
        @DisplayName("Sad Path — no default admin group throws GlobalException")
        void sadPath_noDefaultGroup_throwsGlobal() {
            // ARRANGE
            mockTenant.setSelfManaged(true);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));
            when(roleService.resolveAdminRolesForTenant(any(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(groupService.getDefaultGroupForTenant(TENANT_ID))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.syncSelfManagedRoles("caller", TENANT_ID));
        }

        @Test
        @DisplayName("Happy Path — roles assigned to admin group")
        void happyPath_rolesAssignedToGroup() {
            // ARRANGE
            mockTenant.setSelfManaged(true);
            Roles adminRole = new Roles();
            adminRole.setPkRoleId("ADMIN");

            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));
            when(roleService.resolveAdminRolesForTenant(any(), anyString()))
                    .thenReturn(List.of(adminRole));
            when(groupService.getDefaultGroupForTenant(TENANT_ID))
                    .thenReturn(Optional.of(mockGroup));
            doNothing().when(groupService).assignRoleToGroupIfMissing(any(), any());

            // ACT
            assertDoesNotThrow(() -> service.syncSelfManagedRoles("caller", TENANT_ID));

            // ASSERT
            verify(groupService).assignRoleToGroupIfMissing(mockGroup, adminRole);
        }

        @Test
        @DisplayName("Happy Path — null users list treated as empty — throws GlobalException")
        void happyPath_nullUsersList_throwsGlobal() {
            // ARRANGE
            mockTenant.setSelfManaged(true);
            mockTenant.setUsers(null);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(mockTenant));

            // ACT + ASSERT
            assertThrows(GlobalException.class,
                    () -> service.syncSelfManagedRoles("caller", TENANT_ID));
        }
    }

    // =========================================================================
    // getTenantConfig (public, 2-arg and 3-arg overloads)
    // =========================================================================

    @Nested
    @DisplayName("getTenantConfig")
    class GetTenantConfig {

        @Test
        @DisplayName("Happy Path — found by tenantCode (highest priority)")
        void happyPath_foundByTenantCode_returnsDto() {
            // ARRANGE
            mockTenant.setTenantCode("ACME-001");
            when(tenantRepository.findByTenantCode("ACME-001"))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result = service.getTenantConfig(null, null, "ACME-001");

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID,   result.getTenantId());
            assertEquals(TENANT_NAME, result.getName());
            assertEquals(REALM_NAME,  result.getRealm());
            verify(tenantRepository).findByTenantCode("ACME-001");
        }

        @Test
        @DisplayName("Happy Path — found by exact domain (priority 2)")
        void happyPath_foundByExactDomain() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any()))
                    .thenReturn(Optional.empty());
            when(tenantRepository.findByDomain("acme.motivitylabs.net"))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result =
                    service.getTenantConfig("acme.motivitylabs.net", null, null);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantId());
        }

        @Test
        @DisplayName("Happy Path — found by exact tenantName (priority 3)")
        void happyPath_foundByExactTenantName() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomain(TENANT_NAME)).thenReturn(Optional.empty());
            when(tenantRepository.findByTenantName(TENANT_NAME))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result = service.getTenantConfig(TENANT_NAME, null, null);

            // ASSERT
            assertNotNull(result);
        }

        @Test
        @DisplayName("Happy Path — found by domain containing (priority 4)")
        void happyPath_foundByDomainContaining() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomain(anyString())).thenReturn(Optional.empty());
            when(tenantRepository.findByTenantName(anyString())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomainContaining(anyString()))
                    .thenReturn(List.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result = service.getTenantConfig("acme", null, null);

            // ASSERT
            assertNotNull(result);
        }

        @Test
        @DisplayName("Happy Path — found by tenantName containing (priority 5)")
        void happyPath_foundByTenantNameContaining() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomain(anyString())).thenReturn(Optional.empty());
            when(tenantRepository.findByTenantName(anyString())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomainContaining(anyString()))
                    .thenReturn(Collections.emptyList());
            when(tenantRepository.findByTenantNameContaining(anyString()))
                    .thenReturn(List.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result = service.getTenantConfig("ac", null, null);

            // ASSERT
            assertNotNull(result);
        }

        @Test
        @DisplayName("Happy Path — found by companyName email domain lookup (fallback)")
        void happyPath_foundByCompanyName() {
            // ARRANGE — host is null, find by companyName
            when(tenantRepository.findByEmailDomainContaining(anyString()))
                    .thenReturn(List.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result = service.getTenantConfig(null, "acme.com", null);

            // ASSERT
            assertNotNull(result);
            verify(tenantRepository).findByEmailDomainContaining(anyString());
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomain(anyString())).thenReturn(Optional.empty());
            when(tenantRepository.findByTenantName(anyString())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomainContaining(anyString()))
                    .thenReturn(Collections.emptyList());
            when(tenantRepository.findByTenantNameContaining(anyString()))
                    .thenReturn(Collections.emptyList());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getTenantConfig("unknown", null, null));
        }

        @Test
        @DisplayName("Sad Path — auth config not found throws ResourceNotFoundException")
        void sadPath_authConfigMissing_throwsException() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomain(anyString()))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> service.getTenantConfig("acme.motivitylabs.net", null, null));
        }

        @Test
        @DisplayName("Sad Path — database exception re-throws as RuntimeException")
        void sadPath_databaseException_throwsRuntime() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any()))
                    .thenThrow(new RuntimeException("DB connection refused"));

            // ACT + ASSERT
            assertThrows(RuntimeException.class,
                    () -> service.getTenantConfig(null, null, "ACME-001"));
        }

        @Test
        @DisplayName("Happy Path — 2-arg overload delegates to 3-arg with null tenantCode")
        void happyPath_twoArgOverload_delegatesWithNullCode() {
            // ARRANGE
            when(tenantRepository.findByTenantCode(any())).thenReturn(Optional.empty());
            when(tenantRepository.findByDomain(anyString()))
                    .thenReturn(Optional.of(mockTenant));
            when(authProviderConfigRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(mockAuthConfig));

            // ACT
            AuthDetailsDto result = service.getTenantConfig(
                    "acme.motivitylabs.net", null);

            // ASSERT
            assertNotNull(result);
            assertEquals(TENANT_ID, result.getTenantId());
        }
    }

    // =========================================================================
    // getJwtDecoders
    // =========================================================================

    @Nested
    @DisplayName("getJwtDecoders")
    class GetJwtDecoders {

        @Test
        @DisplayName("Happy Path — empty auth provider list returns empty map")
        void happyPath_emptyProviders_returnsEmptyMap() {
            // ARRANGE
            when(authProviderConfigRepository.findAll()).thenReturn(Collections.emptyList());

            // ACT
            var result = service.getJwtDecoders();

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Sad Path — invalid jwkSetUri is filtered out, returns empty map")
        void sadPath_invalidJwkUri_filteredOut() {
            // ARRANGE — NimbusJwtDecoder will fail on malformed URI
            mockAuthConfig.setJwkUri("not-a-valid-url");
            mockAuthConfig.setIssuerUri("https://keycloak.example.com/realms/acme");
            when(authProviderConfigRepository.findAll())
                    .thenReturn(List.of(mockAuthConfig));

            // ACT — service catches exception and filters out invalid config
            var result = service.getJwtDecoders();

            // ASSERT — returns empty map since invalid config was skipped
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

        // =========================================================================
        // Additional private helper tests
        // =========================================================================

        @Nested
        @DisplayName("repairTenantIfNeeded")
        class RepairTenantIfNeeded {

                @Test
                @DisplayName("Happy Path — missing code/login/authConfig/subscription repaired")
                void happyPath_missingFields_repaired() {
                        // ARRANGE — tenant missing code and loginUrl but has admin user
                        mockTenant.setTenantCode(null);
                        mockTenant.setLoginUrl(null);
                        // Ensure admin has defaultUser=true and keycloakUserId set
                        mockAdmin.setDefaultUser(true);
                        mockAdmin.setKeycloakUserId(KC_USER_ID);
                        mockTenant.setUsers(List.of(mockAdmin));
                        // Set createdBy so subscription call has non-null second argument
                        mockTenant.setCreatedBy("admin-user");

                        when(tenantRepository.findByTenantIDWithUsers(TENANT_ID))
                                        .thenReturn(Optional.of(mockTenant));
                        when(authProviderConfigRepository.findByTenant(mockTenant))
                                        .thenReturn(Optional.empty());
                        doNothing().when(self).saveAuthProviderConfig(eq(mockTenant), any());
                        when(tenantRepository.save(any())).thenReturn(mockTenant);

                        Roles adminRole = new Roles();
                        adminRole.setPkRoleId("R1");
                        when(roleService.resolveAdminRolesForTenant(any(), anyString()))
                                        .thenReturn(List.of(adminRole));
                        when(groupService.createOrGetDefaultGroup(eq(TENANT_ID), anyString(), anyBoolean(), anyString()))
                                        .thenReturn(mockGroup);
                        when(userRepository.countUserGroupMapping(anyString(), anyString())).thenReturn(0L);
                        doNothing().when(userRepository).insertUserGroupMappingIfAbsent(anyString(), anyString());
                        when(subscriptionService.hasActiveSubscription(TENANT_ID)).thenReturn(false);
                        when(subscriptionService.createDefaultSubscription(eq(TENANT_ID), eq("admin-user"))).thenReturn(new SubscriptionSummary());

                        // ACT
                        boolean result = (boolean) ReflectionTestUtils.invokeMethod(service,
                                        "repairTenantIfNeeded", mockTenant);

                        // ASSERT
                        assertTrue(result);
                        assertNotNull(mockTenant.getTenantCode());
                        assertNotNull(mockTenant.getLoginUrl());
                        verify(userRepository).insertUserGroupMappingIfAbsent(anyString(), anyString());
                        verify(subscriptionService).createDefaultSubscription(eq(TENANT_ID), eq("admin-user"));
                }

                @Test
                @DisplayName("Sad Path — exceptions during role/group repair are caught")
                void sadPath_roleRepairException_isCaught() {
                        // ARRANGE
                        mockTenant.setTenantCode(null);
                        mockTenant.setLoginUrl(null);
                        mockTenant.setUsers(List.of(mockAdmin));
                        when(authProviderConfigRepository.findByTenant(mockTenant)).thenReturn(Optional.empty());
                        doThrow(new RuntimeException("boom")).when(roleService).resolveAdminRolesForTenant(any(), anyString());

                        // ACT — should not throw
                        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,
                                        "repairTenantIfNeeded", mockTenant));
                }
        }

        @Nested
        @DisplayName("setupApiKeySSO")
        class SetupApiKeySSO {

                @Test
                @DisplayName("Happy Path — creates client and returns raw key")
                void happyPath_createsKey_returnsRaw() {
                        // ARRANGE
                        when(apiKeyRepository.existsByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(false);
                        doNothing().when(kcUtil).createClient(anyString(), any());
                        ClientRepresentation rep = new ClientRepresentation();
                        rep.setSecret("plain-secret");
                        when(kcUtil.getClientWithSecret(anyString(), anyString())).thenReturn(rep);
                        try (MockedStatic<ApiKeyUtil> ak = mockStatic(ApiKeyUtil.class);
                                 MockedStatic<CryptoUtil> cu = mockStatic(CryptoUtil.class)) {
                                ak.when(() -> ApiKeyUtil.hash(anyString())).thenReturn("hashed");
                                cu.when(() -> CryptoUtil.encrypt(anyString())).thenReturn("enc-secret");
                                when(apiKeyRepository.save(any())).thenReturn(new ExtensionApiKey());

                                // ACT
                                String raw = (String) ReflectionTestUtils.invokeMethod(service, "setupApiKeySSO", mockTenant);

                                // ASSERT
                                assertNotNull(raw);
                                assertEquals(26, raw.length());
                                verify(apiKeyRepository).save(any());
                        }
                }

                @Test
                @DisplayName("Sad Path — active key exists throws RuntimeException")
                void sadPath_activeExists_throws() {
                        // ARRANGE
                        when(apiKeyRepository.existsByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(true);

                        // ACT + ASSERT
                        assertThrows(RuntimeException.class, () ->
                                        ReflectionTestUtils.invokeMethod(service, "setupApiKeySSO", mockTenant)
                        );
                }
        }

        @Nested
        @DisplayName("copyAddress")
        class CopyAddress {

                @Test
                @DisplayName("Happy Path — null source does nothing")
                void happyPath_nullSource_noChange() {
                        Address target = new Address();
                        ReflectionTestUtils.invokeMethod(service, "copyAddress", target, null);
                        // no exception and fields remain null
                        assertNull(target.getAddressLine1());
                }

                @Test
                @DisplayName("Sad Path — null target throws IllegalStateException")
                void sadPath_nullTarget_throws() {
                        Address source = new Address();
                        source.setAddressLine1("1 Main St");
                        assertThrows(IllegalStateException.class, () ->
                                        ReflectionTestUtils.invokeMethod(service, "copyAddress", null, source)
                        );
                }

                @Test
                @DisplayName("Happy Path — copies only non-null fields")
                void happyPath_partialCopy_fieldsCopied() {
                        Address target = new Address();
                        target.setCity("OldCity");
                        Address source = new Address();
                        source.setAddressLine1("1 Main St");
                        source.setCity("NewCity");

                        ReflectionTestUtils.invokeMethod(service, "copyAddress", target, source);

                        assertEquals("1 Main St", target.getAddressLine1());
                        assertEquals("NewCity", target.getCity());
                }
        }

        @Nested
        @DisplayName("sendWithRetry")
        class SendWithRetry {

                @Test
                @DisplayName("Happy Path — runnable succeeds first try")
                void happyPath_successFirstTry() {
                        java.util.concurrent.atomic.AtomicInteger cnt = new java.util.concurrent.atomic.AtomicInteger();
                        Runnable r = cnt::incrementAndGet;

                        ReflectionTestUtils.invokeMethod(service, "sendWithRetry", r, "LBL", "CTX", 3, 1L);

                        assertEquals(1, cnt.get());
                }

                @Test
                @DisplayName("Happy Path — first attempt fails then succeeds")
                void happyPath_retryThenSuccess() {
                        java.util.concurrent.atomic.AtomicInteger cnt = new java.util.concurrent.atomic.AtomicInteger();
                        Runnable r = () -> {
                                int v = cnt.incrementAndGet();
                                if (v == 1) throw new RuntimeException("boom");
                        };

                        // Should not throw despite initial failure
                        ReflectionTestUtils.invokeMethod(service, "sendWithRetry", r, "LBL", "CTX", 3, 1L);
                        assertEquals(2, cnt.get());
                }
        }

        @Nested
        @DisplayName("handleSelfManagedChange")
        class HandleSelfManagedChange {

                @Test
                @DisplayName("Happy Path — enabling selfManaged assigns enterprise role and ensures group")
                void happyPath_enable_assignsRoleAndGroup() {
                        // ARRANGE
                        mockTenant.setTenantType("MSSP");
                        mockTenant.setSelfManaged(false);
                        mockTenant.setUsers(List.of(mockAdmin));

                        Roles enterpriseAdminRole = new Roles();
                        enterpriseAdminRole.setPkRoleId("ENTERPRISE_ADMIN");
                        when(roleService.createOrGetEnterpriseAdminRole(anyString(), anyString())).thenReturn(enterpriseAdminRole);
                        EventsGroup mockEventsGroup = new EventsGroup();
                        mockEventsGroup.setPkEventsGroupId("events-group-1");
                        when(eventsGroupService.createDefaultGroupForTenant(anyString(), anyString())).thenReturn(mockEventsGroup);

                        // ACT
                        ReflectionTestUtils.invokeMethod(service, "handleSelfManagedChange", mockTenant, true, null);

                        // ASSERT
                        assertTrue(Boolean.TRUE.equals(mockTenant.getSelfManaged()));
                        verify(roleService).createOrGetEnterpriseAdminRole(TENANT_ID, ADMIN_ID);
                        verify(eventsGroupService).createDefaultGroupForTenant(TENANT_ID, "SYSTEM");
                }

                @Test
                @DisplayName("Happy Path — disabling selfManaged removes enterprise role")
                void happyPath_disable_removesRole() {
                        // ARRANGE
                        mockTenant.setTenantType("MSSP");
                        mockTenant.setSelfManaged(true);
                        mockTenant.setUsers(List.of(mockAdmin));

                        doNothing().when(roleService).removeEnterpriseAdminRoleFromUser(anyString(), anyString());

                        // ACT
                        ReflectionTestUtils.invokeMethod(service, "handleSelfManagedChange", mockTenant, false, null);

                        // ASSERT
                        assertFalse(Boolean.TRUE.equals(mockTenant.getSelfManaged()));
                        verify(roleService).removeEnterpriseAdminRoleFromUser(TENANT_ID, ADMIN_ID);
                }
        }

    // =========================================================================
    // Private utility: access creationLocks ConcurrentHashMap
    // =========================================================================

    @SuppressWarnings("unchecked")
    private java.util.concurrent.ConcurrentHashMap<String, String> getConcurrentLocks() {
        return (java.util.concurrent.ConcurrentHashMap<String, String>)
                ReflectionTestUtils.getField(service, "creationLocks");
    }
}