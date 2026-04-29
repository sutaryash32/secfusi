package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.ExtensionApiKey;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.ExtensionApiKeyRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.util.ApiKeyUtil;
import com.secufusion.tenant.util.CryptoUtil;
import com.secufusion.tenant.util.KeycloakAdminUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private KeycloakAdminUtil kcUtil;
    @Mock private TenantRepository tenantRepository;
    @Mock private LoginAuditService loginAuditService;
    @Mock private ExtensionApiKeyRepository apiKeyRepository;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private AuthService authService;

    private static final String TENANT_ID = "tenant-123";
    private static final String REALM = "test-realm";
    private static final String TENANT_NAME = "test-tenant";
    private static final String USER_ID = "user-456";
    private static final String SESSION_ID = "session-789";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String KEYCLOAK_URL = "http://keycloak:8080/auth";

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(authService, "keycloakServerUrl", KEYCLOAK_URL);

        tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        tenant.setRealmName(REALM);
        tenant.setTenantName(TENANT_NAME);
    }

    // ==========================================================
    // logout(String tenantId, String refreshToken)
    // ==========================================================

    @Test
    void logout_happyPath_success() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String revokeUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/revoke";
        ResponseEntity<String> successResponse = new ResponseEntity<>("", HttpStatus.OK);
        when(restTemplate.postForEntity(eq(revokeUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(successResponse);

        LogoutResponse result = authService.logout(TENANT_ID, REFRESH_TOKEN);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Logged out successfully", result.getMessage());
        verify(loginAuditService).logLogout(TENANT_ID, REALM, null, null, null, null, false);
    }

    @Test
    void logout_tenantNotFound_throwsResourceNotFoundException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.logout(TENANT_ID, REFRESH_TOKEN));
        verifyNoInteractions(restTemplate, loginAuditService);
    }

    @Test
    void logout_keycloakReturnsNon2xx_failureResponse() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String revokeUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/revoke";
        ResponseEntity<String> badResponse = new ResponseEntity<>("", HttpStatus.BAD_REQUEST);
        when(restTemplate.postForEntity(eq(revokeUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(badResponse);

        LogoutResponse result = authService.logout(TENANT_ID, REFRESH_TOKEN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("400"));
        verify(loginAuditService).logLogoutFailure(eq(TENANT_ID), eq(REALM), isNull(), isNull(), isNull(), contains("400"));
    }

    @Test
    void logout_restTemplateThrowsException_failureResponse() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String revokeUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/revoke";
        when(restTemplate.postForEntity(eq(revokeUrl), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        LogoutResponse result = authService.logout(TENANT_ID, REFRESH_TOKEN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
        verify(loginAuditService).logLogoutFailure(eq(TENANT_ID), isNull(), isNull(), isNull(), isNull(), contains("Connection refused"));
    }

    // ==========================================================
    // logoutUser(String tenantId, String userId)
    // ==========================================================

    @Test
    void logoutUser_happyPath() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.logoutUser(REALM, USER_ID)).thenReturn(3);

        LogoutResponse result = authService.logoutUser(TENANT_ID, USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getSessionsRevoked());
        verify(loginAuditService).logLogout(TENANT_ID, REALM, USER_ID, null, null, null, true);
    }

    @Test
    void logoutUser_tenantNotFound_throwsException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.logoutUser(TENANT_ID, USER_ID));
    }

    @Test
    void logoutUser_kcUtilThrows_failureResponse() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.logoutUser(REALM, USER_ID)).thenThrow(new RuntimeException("KC error"));

        LogoutResponse result = authService.logoutUser(TENANT_ID, USER_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("KC error"));
        verify(loginAuditService).logLogoutFailure(TENANT_ID, REALM, USER_ID, null, null, "KC error");
    }

    // ==========================================================
    // logoutUserByUsername(String tenantId, String username)
    // ==========================================================

    @Test
    void logoutUserByUsername_happyPath() {
        String username = "john.doe";
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<org.keycloak.representations.idm.UserRepresentation> users = new ArrayList<>();
        org.keycloak.representations.idm.UserRepresentation kcUser = new org.keycloak.representations.idm.UserRepresentation();
        kcUser.setId(USER_ID);
        users.add(kcUser);
        when(kcUtil.findUserByUsername(REALM, username)).thenReturn(users);
        when(kcUtil.logoutUser(REALM, USER_ID)).thenReturn(1);

        LogoutResponse result = authService.logoutUserByUsername(TENANT_ID, username);

        assertTrue(result.isSuccess());
    }

    @Test
    void logoutUserByUsername_userNotFound_failure() {
        String username = "unknown";
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.findUserByUsername(REALM, username)).thenReturn(Collections.emptyList());

        LogoutResponse result = authService.logoutUserByUsername(TENANT_ID, username);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("User not found"));
    }

    // ==========================================================
    // logoutAllUsers(String tenantId)
    // ==========================================================

    @Test
    void logoutAllUsers_happyPath() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.logoutAllUsers(REALM)).thenReturn(42);

        LogoutResponse result = authService.logoutAllUsers(TENANT_ID);

        assertTrue(result.isSuccess());
        assertEquals(42, result.getSessionsRevoked());
        verify(loginAuditService).logLogout(TENANT_ID, REALM, null, null, null, null, true);
    }

    @Test
    void logoutAllUsers_kcUtilThrows_failure() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.logoutAllUsers(REALM)).thenThrow(new RuntimeException("error"));

        LogoutResponse result = authService.logoutAllUsers(TENANT_ID);

        assertFalse(result.isSuccess());
    }

    // ==========================================================
    // revokeSession(String tenantId, String sessionId)
    // ==========================================================

    @Test
    void revokeSession_happyPath() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        doNothing().when(kcUtil).revokeSession(REALM, SESSION_ID);

        LogoutResponse result = authService.revokeSession(TENANT_ID, SESSION_ID);

        assertTrue(result.isSuccess());
        verify(loginAuditService).logSessionRevoked(TENANT_ID, REALM, null, null, SESSION_ID, "admin");
    }

    @Test
    void revokeSession_kcUtilThrows_failure() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        doThrow(new RuntimeException("session error")).when(kcUtil).revokeSession(REALM, SESSION_ID);

        LogoutResponse result = authService.revokeSession(TENANT_ID, SESSION_ID);
        assertFalse(result.isSuccess());
    }

    // ==========================================================
    // getUserSessions(String tenantId, String userId)
    // ==========================================================

    @Test
    void getUserSessions_returnsList() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<UserSessionRepresentation> sessions = List.of(new UserSessionRepresentation());
        when(kcUtil.getUserSessions(REALM, USER_ID)).thenReturn(sessions);

        List<UserSessionRepresentation> result = authService.getUserSessions(TENANT_ID, USER_ID);
        assertEquals(1, result.size());
    }

    // ==========================================================
    // getActiveSessionCount(String tenantId)
    // ==========================================================

    @Test
    void getActiveSessionCount_returnsInt() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.getActiveSessionCount(REALM)).thenReturn(5);
        assertEquals(5, authService.getActiveSessionCount(TENANT_ID));
    }

    // ==========================================================
    // getAllSessions(String tenantId, int page, int size)
    // ==========================================================

    @Test
    void getAllSessions_happyPath() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<UserSessionRepresentation> kcSessions = new ArrayList<>();
        UserSessionRepresentation us = new UserSessionRepresentation();
        us.setId("s1");
        us.setUserId(USER_ID);
        us.setUsername("john");
        us.setIpAddress("127.0.0.1");
        us.setStart(1000L);
        us.setLastAccess(2000L);
        kcSessions.add(us);
        when(kcUtil.getAllSessions(REALM, 0, 10)).thenReturn(kcSessions);

        SessionListResponse response = authService.getAllSessions(TENANT_ID, 0, 10);
        assertNotNull(response);
        assertEquals(1, response.getSessions().size());
        assertEquals("s1", response.getSessions().get(0).getSessionId());
    }

    // ==========================================================
    // getUserSessionsDTO(String tenantId, String userId)
    // ==========================================================

    @Test
    void getUserSessionsDTO_returnsDTOs() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        UserSessionRepresentation us = new UserSessionRepresentation();
        us.setId("s2");
        us.setUserId(USER_ID);
        us.setUsername("user");
        us.setStart(1L);
        us.setLastAccess(2L);
        when(kcUtil.getUserSessions(REALM, USER_ID)).thenReturn(List.of(us));

        SessionListResponse resp = authService.getUserSessionsDTO(TENANT_ID, USER_ID);
        assertEquals(1, resp.getSessions().size());
    }

    // ==========================================================
    // getClientSessions(String tenantId, String clientId)
    // ==========================================================

    @Test
    void getClientSessions_returnsSessions() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        UserSessionRepresentation us = new UserSessionRepresentation();
        us.setId("s3");
        when(kcUtil.getClientSessions(REALM, "client-1")).thenReturn(List.of(us));

        SessionListResponse resp = authService.getClientSessions(TENANT_ID, "client-1");
        assertEquals(1, resp.getSessions().size());
    }

    // ==========================================================
    // getSessionStats(String tenantId)
    // ==========================================================

    @Test
    void getSessionStats_returnsMap() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        Map<String, Long> stats = Map.of("client1", 3L);
        when(kcUtil.getSessionStats(REALM)).thenReturn(stats);

        Map<String, Long> result = authService.getSessionStats(TENANT_ID);
        assertEquals(3L, result.get("client1"));
    }

    // ==========================================================
    // revokeUserOfflineSessions(String tenantId, String userId)
    // ==========================================================

    @Test
    void revokeUserOfflineSessions_success() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        doNothing().when(kcUtil).revokeUserOfflineSessions(REALM, USER_ID);

        LogoutResponse result = authService.revokeUserOfflineSessions(TENANT_ID, USER_ID);
        assertTrue(result.isSuccess());
    }

    @Test
    void revokeUserOfflineSessions_kcUtilThrows_failure() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        doThrow(new RuntimeException("offline error")).when(kcUtil).revokeUserOfflineSessions(REALM, USER_ID);

        LogoutResponse result = authService.revokeUserOfflineSessions(TENANT_ID, USER_ID);
        assertFalse(result.isSuccess());
    }

    // ==========================================================
    // getUserOfflineSessions(String tenantId, String userId)
    // ==========================================================

    @Test
    void getUserOfflineSessions_returnsSessions() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        UserSessionRepresentation us = new UserSessionRepresentation();
        us.setId("offline1");
        when(kcUtil.getUserOfflineSessions(REALM, USER_ID, TENANT_NAME)).thenReturn(List.of(us));

        SessionListResponse resp = authService.getUserOfflineSessions(TENANT_ID, USER_ID);
        assertEquals(1, resp.getSessions().size());
    }

    // ==========================================================
    // revokeBulkSessions(String tenantId, List<String> sessionIds)
    // ==========================================================

    @Test
    void revokeBulkSessions_allSuccess() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<String> ids = List.of("s1", "s2");
        doNothing().when(kcUtil).revokeSession(REALM, "s1");
        doNothing().when(kcUtil).revokeSession(REALM, "s2");

        LogoutResponse result = authService.revokeBulkSessions(TENANT_ID, ids);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getSessionsRevoked());
        verify(loginAuditService).logBulkSessionRevoke(TENANT_ID, REALM, 2, 0, "admin");
    }

    @Test
    void revokeBulkSessions_partialFailure() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<String> ids = List.of("s1", "s2");
        doNothing().when(kcUtil).revokeSession(REALM, "s1");
        doThrow(new RuntimeException("fail")).when(kcUtil).revokeSession(REALM, "s2");

        LogoutResponse result = authService.revokeBulkSessions(TENANT_ID, ids);
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(1, result.getSessionsRevoked());
        assertEquals(List.of("s2"), result.getFailedSessionIds());
    }

    // ==========================================================
    // introspectToken(String tenantId, String token)
    // ==========================================================

    @Test
    void introspectToken_activeToken_returnsActiveResponse() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String introspectUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token/introspect";
        Map<String, Object> body = new HashMap<>();
        body.put("active", true);
        body.put("client_id", "client1");
        body.put("username", "john");
        body.put("sub", "sub123");
        body.put("scope", "openid profile");
        body.put("exp", (Number) (System.currentTimeMillis() / 1000L + 3600));
        body.put("iat", (Number) (System.currentTimeMillis() / 1000L));
        body.put("sid", "session-1");
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.postForEntity(eq(introspectUrl), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        TokenIntrospectionResponse result = authService.introspectToken(TENANT_ID, "token");
        assertTrue(result.isActive());
        assertEquals("john", result.getUsername());
        assertEquals(2, result.getScope().size());
    }

    @Test
    void introspectToken_inactiveToken_returnsInactive() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String introspectUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token/introspect";
        Map<String, Object> body = new HashMap<>();
        body.put("active", false);
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.postForEntity(eq(introspectUrl), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        TokenIntrospectionResponse result = authService.introspectToken(TENANT_ID, "token");
        assertFalse(result.isActive());
    }

    @Test
    void introspectToken_httpFailure_returnsError() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String introspectUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token/introspect";
        when(restTemplate.postForEntity(eq(introspectUrl), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        TokenIntrospectionResponse result = authService.introspectToken(TENANT_ID, "token");
        // Use isError() if that method exists; otherwise check for error message
        assertNotNull(result);
        assertFalse(result.isActive());
        assertTrue(result.getError() != null && !result.getError().isEmpty());
    }

    // ==========================================================
    // resolveTenantIdByName(String tenantName)
    // ==========================================================

    @Test
    void resolveTenantIdByName_found() {
        when(tenantRepository.findByTenantName(TENANT_NAME)).thenReturn(Optional.of(tenant));
        assertEquals(TENANT_ID, authService.resolveTenantIdByName(TENANT_NAME));
    }

    @Test
    void resolveTenantIdByName_notFound_throwsException() {
        when(tenantRepository.findByTenantName("unknown")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.resolveTenantIdByName("unknown"));
    }

    // ==========================================================
    // refreshToken(String tenantName, String refreshToken)
    // ==========================================================

    @Test
    void refreshToken_success() {
        when(tenantRepository.findByTenantName(TENANT_NAME)).thenReturn(Optional.of(tenant));
        String tokenUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token";
        Map<String, Object> body = new HashMap<>();
        body.put("access_token", "new-access");
        body.put("refresh_token", "new-refresh");
        body.put("expires_in", 300);
        body.put("refresh_expires_in", 1800);
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.postForEntity(eq(tokenUrl), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        TokenRefreshResponse result = authService.refreshToken(TENANT_NAME, REFRESH_TOKEN);
        assertTrue(result.isSuccess());
        assertEquals("new-access", result.getAccessToken());
    }

    @Test
    void refreshToken_failure() {
        when(tenantRepository.findByTenantName(TENANT_NAME)).thenReturn(Optional.of(tenant));
        String tokenUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token";
        when(restTemplate.postForEntity(eq(tokenUrl), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("network error"));

        TokenRefreshResponse result = authService.refreshToken(TENANT_NAME, REFRESH_TOKEN);
        assertFalse(result.isSuccess());
    }

    // ==========================================================
    // changePassword(String tenantId, String userId, PasswordChangeRequest)
    // ==========================================================

    @Test
    void changePassword_passwordsDontMatch_failure() {
        PasswordChangeRequest req = new PasswordChangeRequest("old", "new1", "new2");
        PasswordChangeResponse resp = authService.changePassword(TENANT_ID, USER_ID, req);
        assertFalse(resp.isSuccess());
        verifyNoInteractions(tenantRepository);
    }

    @Test
    void changePassword_currentPasswordInvalid_failure() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        PasswordChangeRequest req = new PasswordChangeRequest("wrong", "new", "new");
        when(kcUtil.verifyUserPassword(REALM, USER_ID, "wrong")).thenReturn(false);

        PasswordChangeResponse resp = authService.changePassword(TENANT_ID, USER_ID, req);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("Current password is incorrect"));
    }

    @Test
    void changePassword_success() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        PasswordChangeRequest req = new PasswordChangeRequest("correct", "newPass", "newPass");
        when(kcUtil.verifyUserPassword(REALM, USER_ID, "correct")).thenReturn(true);
        doNothing().when(kcUtil).setUserPassword(REALM, USER_ID, "newPass", false);

        PasswordChangeResponse resp = authService.changePassword(TENANT_ID, USER_ID, req);
        assertTrue(resp.isSuccess());
        verify(loginAuditService).logPasswordChange(TENANT_ID, REALM, USER_ID, "self");
    }

    // ==========================================================
    // forcePasswordReset(String tenantId, String userId)
    // ==========================================================

    @Test
    void forcePasswordReset_success() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        doNothing().when(kcUtil).setRequiredAction(REALM, USER_ID, "UPDATE_PASSWORD");

        PasswordChangeResponse resp = authService.forcePasswordReset(TENANT_ID, USER_ID);
        assertTrue(resp.isSuccess());
        verify(loginAuditService).logPasswordReset(TENANT_ID, REALM, USER_ID, "admin");
    }

    @Test
    void forcePasswordReset_failure() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        doThrow(new RuntimeException("error")).when(kcUtil).setRequiredAction(anyString(), anyString(), anyString());

        PasswordChangeResponse resp = authService.forcePasswordReset(TENANT_ID, USER_ID);
        assertFalse(resp.isSuccess());
    }

    // ==========================================================
    // searchSessions(String tenantId, SessionSearchRequest)
    // ==========================================================

    @Test
    void searchSessions_filtersCorrectly_paginates() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<UserSessionRepresentation> allSessions = createSampleSessions(5);
        when(kcUtil.getAllSessions(REALM, 0, 1000)).thenReturn(allSessions);

        SessionSearchRequest request = new SessionSearchRequest();
        request.setUsername("User1");
        request.setPage(0);
        request.setSize(10);

        SessionListResponse result = authService.searchSessions(TENANT_ID, request);
        assertNotNull(result);
        assertEquals(1, result.getSessions().size()); // only User1 matches
    }

    @Test
    void searchSessions_emptyResult() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.getAllSessions(anyString(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        SessionSearchRequest request = new SessionSearchRequest();
        request.setUsername("nobody");
        request.setPage(0);
        request.setSize(10);

        SessionListResponse result = authService.searchSessions(TENANT_ID, request);
        assertNotNull(result);
        assertTrue(result.getSessions().isEmpty());
    }

    // ==========================================================
    // exchangeApiKeyForToken(String rawApiKey)
    // ==========================================================

    @Test
    void exchangeApiKeyForToken_blankKey_failure() {
        ApiKeyTokenResponse resp = authService.exchangeApiKeyForToken("");
        assertNull(resp);
    }

    @Test
    void exchangeApiKeyForToken_keyNotFound_failure() {
        try (MockedStatic<ApiKeyUtil> apiMock = mockStatic(ApiKeyUtil.class)) {
            String raw = "valid-raw-key";
            String hash = "someHash";
            apiMock.when(() -> ApiKeyUtil.hash(raw)).thenReturn(hash);
            when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.empty());

            ApiKeyTokenResponse resp = authService.exchangeApiKeyForToken(raw);

            assertNull(resp);
        }
    }

    @Test
    void exchangeApiKeyForToken_notActive_failure() {
        try (MockedStatic<ApiKeyUtil> apiMock = mockStatic(ApiKeyUtil.class)) {
            String raw = "raw-key";
            String hash = "hashed";
            apiMock.when(() -> ApiKeyUtil.hash(raw)).thenReturn(hash);
            ExtensionApiKey apiKey = createApiKey(hash);
            apiKey.setStatus("INACTIVE");
            when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(apiKey));

            ApiKeyTokenResponse resp = authService.exchangeApiKeyForToken(raw);

            assertNull(resp);
        }
    }

    @Test
    void exchangeApiKeyForToken_expired_failure() {
        try (MockedStatic<ApiKeyUtil> apiMock = mockStatic(ApiKeyUtil.class)) {
            String raw = "raw-key";
            String hash = "hashed";
            apiMock.when(() -> ApiKeyUtil.hash(raw)).thenReturn(hash);
            ExtensionApiKey apiKey = createApiKey(hash);
            apiKey.setStatus("ACTIVE");
            apiKey.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(apiKey));

            ApiKeyTokenResponse resp = authService.exchangeApiKeyForToken(raw);

            assertNull(resp);
        }
    }

    @Test
    void exchangeApiKeyForToken_tenantNotFound_failure() {
        try (MockedStatic<ApiKeyUtil> apiMock = mockStatic(ApiKeyUtil.class)) {
            String raw = "raw-key";
            String hash = "hashed";
            apiMock.when(() -> ApiKeyUtil.hash(raw)).thenReturn(hash);
            ExtensionApiKey apiKey = createApiKey(hash);
            apiKey.setStatus("ACTIVE");
            when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(apiKey));
            when(tenantRepository.findById(apiKey.getTenantId())).thenReturn(Optional.empty());

            ApiKeyTokenResponse resp = authService.exchangeApiKeyForToken(raw);

            assertNull(resp);
        }
    }

    @Test
    void exchangeApiKeyForToken_success() {
        try (MockedStatic<ApiKeyUtil> apiMock = mockStatic(ApiKeyUtil.class);
             MockedStatic<CryptoUtil> cryptoMock = mockStatic(CryptoUtil.class)) {
            String raw = "raw-key";
            String hash = "hashed";
            apiMock.when(() -> ApiKeyUtil.hash(raw)).thenReturn(hash);
            ExtensionApiKey apiKey = createApiKey(hash);
            when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(apiKey));
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            cryptoMock.when(() -> CryptoUtil.decrypt(apiKey.getClientSecret())).thenReturn("plain-secret");

            String tokenUrl = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token";
            Map<String, Object> tokenResponse = new HashMap<>();
            tokenResponse.put("access_token", "access-123");
            tokenResponse.put("token_type", "Bearer");
            tokenResponse.put("expires_in", 300);
            ResponseEntity<Map> responseEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
            when(restTemplate.postForEntity(eq(tokenUrl), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(responseEntity);

            ApiKeyTokenResponse result = authService.exchangeApiKeyForToken(raw);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(TENANT_NAME, result.getTenantName());
            assertEquals("access-123", result.getAccessToken());
            verify(apiKeyRepository).save(apiKey);
        }
    }

    // ==========================================================
    // Additional extended session tests
    // ==========================================================

    @Test
    void getSessionById_found() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        UserSessionRepresentation us = new UserSessionRepresentation();
        us.setId(SESSION_ID);
        when(kcUtil.getSessionById(REALM, SESSION_ID)).thenReturn(Optional.of(us));
        Optional<SessionDTO> result = authService.getSessionById(TENANT_ID, SESSION_ID);
        assertTrue(result.isPresent());
        assertEquals(SESSION_ID, result.get().getSessionId());
    }

    @Test
    void getSessionById_notFound() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.getSessionById(REALM, SESSION_ID)).thenReturn(Optional.empty());
        Optional<SessionDTO> result = authService.getSessionById(TENANT_ID, SESSION_ID);
        assertFalse(result.isPresent());
    }

    @Test
    void getUserSessionCount() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(kcUtil.getUserSessionCount(REALM, USER_ID)).thenReturn(3);
        assertEquals(3, authService.getUserSessionCount(TENANT_ID, USER_ID));
    }

    @Test
    void getActiveUsers_returnsGroupedList() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<UserSessionRepresentation> sessions = createActiveUsersSessions();
        when(kcUtil.getAllSessions(REALM, 0, 10000)).thenReturn(sessions);

        List<ActiveUserDTO> users = authService.getActiveUsers(TENANT_ID);
        assertEquals(2, users.size());
        assertEquals("User0", users.get(0).getUsername());
    }

    @Test
    void logoutOtherSessions_positive() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        String currentSession = "keep-this";
        List<UserSessionRepresentation> sessions = new ArrayList<>();
        UserSessionRepresentation keepSession = new UserSessionRepresentation();
        keepSession.setId(currentSession);
        keepSession.setUserId(USER_ID);
        UserSessionRepresentation otherSession = new UserSessionRepresentation();
        otherSession.setId("other-session");
        otherSession.setUserId(USER_ID);
        sessions.add(keepSession);
        sessions.add(otherSession);
        when(kcUtil.getUserSessions(REALM, USER_ID)).thenReturn(sessions);
        doNothing().when(kcUtil).revokeSession(REALM, "other-session");

        LogoutResponse result = authService.logoutOtherSessions(TENANT_ID, USER_ID, currentSession);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSessionsRevoked());
    }

    @Test
    void getSessionSummary_returnsSummary() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        List<UserSessionRepresentation> sessions = createSampleSessions(3);
        when(kcUtil.getAllSessions(REALM, 0, 10000)).thenReturn(sessions);
        Map<String, Long> clientStats = Map.of("client1", 3L);
        when(kcUtil.getSessionStats(REALM)).thenReturn(clientStats);

        SessionSummaryDTO summary = authService.getSessionSummary(TENANT_ID);
        assertEquals(3, summary.getTotalActiveSessions());
        assertEquals(3, summary.getUniqueActiveUsers());
        assertNotNull(summary.getAvgSessionDurationFormatted());
    }

    // ==========================================================
    // Helper methods
    // ==========================================================

    private ExtensionApiKey createApiKey(String keyHash) {
        ExtensionApiKey key = new ExtensionApiKey();
        key.setKeyHash(keyHash);
        key.setStatus("ACTIVE");
        key.setTenantId(TENANT_ID);
        key.setClientId("client-id");
        key.setClientSecret("encrypted");
        return key;
    }

    private List<UserSessionRepresentation> createSampleSessions(int count) {
        List<UserSessionRepresentation> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserSessionRepresentation us = new UserSessionRepresentation();
            us.setId("session-" + i);
            us.setUserId("user-" + i);
            us.setUsername("User" + i);
            us.setIpAddress("10.0.0." + i);
            us.setStart(1000L + i * 100);
            us.setLastAccess(2000L + i * 100);
            sessions.add(us);
        }
        return sessions;
    }

    private List<UserSessionRepresentation> createActiveUsersSessions() {
        List<UserSessionRepresentation> list = new ArrayList<>();
        UserSessionRepresentation u1 = new UserSessionRepresentation();
        u1.setUserId("uid1");
        u1.setUsername("User0");
        u1.setLastAccess(3000L);
        u1.setStart(1000L);
        UserSessionRepresentation u2 = new UserSessionRepresentation();
        u2.setUserId("uid2");
        u2.setUsername("User1");
        u2.setLastAccess(2000L);
        u2.setStart(500L);
        list.add(u1);
        list.add(u2);
        return list;
    }
}