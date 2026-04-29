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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakAdminUtil kcUtil;
    private final TenantRepository tenantRepository;
    private final LoginAuditService loginAuditService;
    private final ExtensionApiKeyRepository apiKeyRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.admin.server-url}")
    private String keycloakServerUrl;

    /**
     * Logout current user by revoking their refresh token.
     *
     * @param tenantId     the tenant ID
     * @param refreshToken the refresh token to revoke
     * @return logout response
     */
    public LogoutResponse logout(String tenantId, String refreshToken) {
        log.info("Processing logout for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        String realm = tenant.getRealmName();
        String clientId = tenant.getTenantName();

        try {
            // Revoke refresh token via Keycloak token revocation endpoint
            String revokeUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/revoke";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("token", refreshToken);
            body.add("token_type_hint", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(revokeUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Refresh token revoked successfully for tenantId={}", tenantId);
                // Log audit event
                loginAuditService.logLogout(tenantId, realm, null, null, null, null, false);
                return LogoutResponse.success("Logged out successfully");
            } else {
                log.warn("Token revocation returned status {} for tenantId={}", response.getStatusCode(), tenantId);
                loginAuditService.logLogoutFailure(tenantId, realm, null, null, null,
                        "Logout failed: " + response.getStatusCode());
                return LogoutResponse.failure("Logout failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to logout for tenantId={}: {}", tenantId, e.getMessage(), e);
            loginAuditService.logLogoutFailure(tenantId, null, null, null, null, e.getMessage());
            return LogoutResponse.failure("Logout failed: " + e.getMessage());
        }
    }

    /**
     * Logout a specific user by their user ID (revokes all sessions).
     *
     * @param tenantId the tenant ID
     * @param userId   the user ID (Keycloak user ID)
     * @return logout response
     */
    public LogoutResponse logoutUser(String tenantId, String userId) {
        log.info("Processing logout for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        try {
            int sessionsRevoked = kcUtil.logoutUser(tenant.getRealmName(), userId);
            log.info("User {} logged out, {} sessions revoked", userId, sessionsRevoked);
            // Log audit event
            loginAuditService.logLogout(tenantId, tenant.getRealmName(), userId, null, null, null, true);
            return LogoutResponse.success("User logged out successfully", sessionsRevoked);
        } catch (Exception e) {
            log.error("Failed to logout user {} in tenantId={}: {}", userId, tenantId, e.getMessage(), e);
            loginAuditService.logLogoutFailure(tenantId, tenant.getRealmName(), userId, null, null, e.getMessage());
            return LogoutResponse.failure("Logout failed: " + e.getMessage());
        }
    }

    /**
     * Logout user by username/email.
     *
     * @param tenantId the tenant ID
     * @param username the username or email
     * @return logout response
     */
    public LogoutResponse logoutUserByUsername(String tenantId, String username) {
        log.info("Processing logout for username {} in tenantId={}", username, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Find user in Keycloak
        List<org.keycloak.representations.idm.UserRepresentation> users =
                kcUtil.findUserByUsername(tenant.getRealmName(), username);

        if (users.isEmpty()) {
            return LogoutResponse.failure("User not found: " + username);
        }

        String kcUserId = users.get(0).getId();
        return logoutUser(tenantId, kcUserId);
    }

    /**
     * Logout all users from a tenant (revokes all sessions in the realm).
     *
     * @param tenantId the tenant ID
     * @return logout response
     */
    public LogoutResponse logoutAllUsers(String tenantId) {
        log.info("Processing logout for all users in tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        try {
            int sessionsRevoked = kcUtil.logoutAllUsers(tenant.getRealmName());
            log.info("All users logged out from tenantId={}, {} sessions revoked", tenantId, sessionsRevoked);
            // Log audit event
            loginAuditService.logLogout(tenantId, tenant.getRealmName(), null, null, null, null, true);
            return LogoutResponse.success("All users logged out successfully", sessionsRevoked);
        } catch (Exception e) {
            log.error("Failed to logout all users in tenantId={}: {}", tenantId, e.getMessage(), e);
            loginAuditService.logLogoutFailure(tenantId, tenant.getRealmName(), null, null, null,
                    "Logout all failed: " + e.getMessage());
            return LogoutResponse.failure("Logout failed: " + e.getMessage());
        }
    }

    /**
     * Revoke a specific session by session ID.
     *
     * @param tenantId  the tenant ID
     * @param sessionId the session ID to revoke
     * @return logout response
     */
    public LogoutResponse revokeSession(String tenantId, String sessionId) {
        log.info("Revoking session {} in tenantId={}", sessionId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        try {
            kcUtil.revokeSession(tenant.getRealmName(), sessionId);
            log.info("Session {} revoked in tenantId={}", sessionId, tenantId);
            // Log audit event
            loginAuditService.logSessionRevoked(tenantId, tenant.getRealmName(), null, null, sessionId, "admin");
            return LogoutResponse.success("Session revoked successfully");
        } catch (Exception e) {
            log.error("Failed to revoke session {} in tenantId={}: {}", sessionId, tenantId, e.getMessage(), e);
            return LogoutResponse.failure("Session revocation failed: " + e.getMessage());
        }
    }

    /**
     * Get active sessions for a user.
     *
     * @param tenantId the tenant ID
     * @param userId   the Keycloak user ID
     * @return list of active sessions
     */
    public List<UserSessionRepresentation> getUserSessions(String tenantId, String userId) {
        log.debug("Getting sessions for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        return kcUtil.getUserSessions(tenant.getRealmName(), userId);
    }

    /**
     * Get active session count for a tenant.
     *
     * @param tenantId the tenant ID
     * @return number of active sessions
     */
    public int getActiveSessionCount(String tenantId) {
        log.debug("Getting active session count for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        return kcUtil.getActiveSessionCount(tenant.getRealmName());
    }

    // ============================================================
    // SESSION MANAGEMENT
    // ============================================================

    /**
     * Get all active sessions in a tenant (paginated).
     *
     * @param tenantId the tenant ID
     * @param page     page number (0-based)
     * @param size     page size
     * @return list of sessions
     */
    public SessionListResponse getAllSessions(String tenantId, int page, int size) {
        log.info("Getting all sessions for tenantId={} (page={}, size={})", tenantId, page, size);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> sessions = kcUtil.getAllSessions(
                tenant.getRealmName(), page * size, size);

        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(SessionDTO::fromKeycloakSession)
                .collect(Collectors.toList());

        return SessionListResponse.of(sessionDTOs);
    }

    /**
     * Get sessions for a specific user as DTOs.
     *
     * @param tenantId the tenant ID
     * @param userId   the Keycloak user ID
     * @return session list response
     */
    public SessionListResponse getUserSessionsDTO(String tenantId, String userId) {
        log.debug("Getting sessions DTO for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> sessions = kcUtil.getUserSessions(tenant.getRealmName(), userId);

        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(SessionDTO::fromKeycloakSession)
                .collect(Collectors.toList());

        return SessionListResponse.of(sessionDTOs);
    }

    /**
     * Get sessions for a specific client.
     *
     * @param tenantId the tenant ID
     * @param clientId the client ID
     * @return session list response
     */
    public SessionListResponse getClientSessions(String tenantId, String clientId) {
        log.info("Getting sessions for client {} in tenantId={}", clientId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> sessions = kcUtil.getClientSessions(tenant.getRealmName(), clientId);

        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(SessionDTO::fromKeycloakSession)
                .collect(Collectors.toList());

        return SessionListResponse.of(sessionDTOs);
    }

    /**
     * Get session statistics for a tenant.
     *
     * @param tenantId the tenant ID
     * @return map of client ID to active session count
     */
    public Map<String, Long> getSessionStats(String tenantId) {
        log.debug("Getting session stats for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        return kcUtil.getSessionStats(tenant.getRealmName());
    }

    /**
     * Revoke all offline sessions for a user.
     *
     * @param tenantId the tenant ID
     * @param userId   the Keycloak user ID
     * @return logout response
     */
    public LogoutResponse revokeUserOfflineSessions(String tenantId, String userId) {
        log.info("Revoking offline sessions for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        try {
            kcUtil.revokeUserOfflineSessions(tenant.getRealmName(), userId);
            return LogoutResponse.success("Offline sessions revoked successfully");
        } catch (Exception e) {
            log.error("Failed to revoke offline sessions for user {} in tenantId={}: {}",
                    userId, tenantId, e.getMessage(), e);
            return LogoutResponse.failure("Failed to revoke offline sessions: " + e.getMessage());
        }
    }

    /**
     * Get offline sessions for a user.
     *
     * @param tenantId the tenant ID
     * @param userId   the Keycloak user ID
     * @return session list response
     */
    public SessionListResponse getUserOfflineSessions(String tenantId, String userId) {
        log.debug("Getting offline sessions for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> sessions = kcUtil.getUserOfflineSessions(
                tenant.getRealmName(), userId, tenant.getTenantName());

        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(SessionDTO::fromKeycloakSession)
                .collect(Collectors.toList());

        return SessionListResponse.of(sessionDTOs);
    }

    // ============================================================
    // BULK SESSION OPERATIONS
    // ============================================================

    /**
     * Revoke multiple sessions in bulk.
     *
     * @param tenantId   the tenant ID
     * @param sessionIds list of session IDs to revoke
     * @return logout response with details of revoked/failed sessions
     */
    public LogoutResponse revokeBulkSessions(String tenantId, List<String> sessionIds) {
        log.info("Revoking {} sessions in bulk for tenantId={}", sessionIds.size(), tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<String> revokedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        for (String sessionId : sessionIds) {
            try {
                kcUtil.revokeSession(tenant.getRealmName(), sessionId);
                revokedIds.add(sessionId);
                log.debug("Session {} revoked successfully", sessionId);
            } catch (Exception e) {
                log.warn("Failed to revoke session {}: {}", sessionId, e.getMessage());
                failedIds.add(sessionId);
            }
        }

        // Log audit event for bulk operation
        loginAuditService.logBulkSessionRevoke(tenantId, tenant.getRealmName(),
                revokedIds.size(), failedIds.size(), "admin");

        if (failedIds.isEmpty()) {
            return LogoutResponse.successBulk(
                    "All sessions revoked successfully",
                    revokedIds, failedIds, sessionIds.size());
        } else if (revokedIds.isEmpty()) {
            return LogoutResponse.failure("Failed to revoke any sessions");
        } else {
            return LogoutResponse.partialSuccess(
                    String.format("Partially completed: %d revoked, %d failed",
                            revokedIds.size(), failedIds.size()),
                    revokedIds, failedIds);
        }
    }

    // ============================================================
    // TOKEN OPERATIONS
    // ============================================================

    /**
     * Introspect a token to get its details and validity.
     *
     * @param tenantId the tenant ID
     * @param token    the token to introspect
     * @return token introspection response
     */
    public TokenIntrospectionResponse introspectToken(String tenantId, String token) {
        log.info("Introspecting token for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        String realm = tenant.getRealmName();
        String clientId = tenant.getTenantName();

        try {
            String introspectUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token/introspect";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(introspectUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                boolean active = Boolean.TRUE.equals(responseBody.get("active"));

                if (!active) {
                    return TokenIntrospectionResponse.inactive();
                }

                List<String> scopes = null;
                if (responseBody.get("scope") != null) {
                    scopes = Arrays.asList(((String) responseBody.get("scope")).split(" "));
                }

                LocalDateTime expiresAt = null;
                if (responseBody.get("exp") != null) {
                    long exp = ((Number) responseBody.get("exp")).longValue();
                    expiresAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(exp), ZoneId.systemDefault());
                }

                LocalDateTime issuedAt = null;
                if (responseBody.get("iat") != null) {
                    long iat = ((Number) responseBody.get("iat")).longValue();
                    issuedAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(iat), ZoneId.systemDefault());
                }

                return TokenIntrospectionResponse.active(
                        (String) responseBody.get("client_id"),
                        (String) responseBody.get("username"),
                        (String) responseBody.get("sub"),
                        scopes,
                        expiresAt,
                        issuedAt,
                        (String) responseBody.get("sid")
                );
            }

            return TokenIntrospectionResponse.error("Introspection failed");
        } catch (Exception e) {
            log.error("Token introspection failed for tenantId={}: {}", tenantId, e.getMessage(), e);
            return TokenIntrospectionResponse.error("Introspection failed: " + e.getMessage());
        }
    }

    /**
     * Resolve tenant ID from tenant name.
     *
     * @param tenantName the tenant name
     * @return the tenant ID
     */
    public String resolveTenantIdByName(String tenantName) {
        return tenantRepository.findByTenantName(tenantName)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantName))
                .getTenantID();
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * @param tenantName   the tenant name
     * @param refreshToken the refresh token
     * @return token refresh response
     */
    public TokenRefreshResponse refreshToken(String tenantName, String refreshToken) {
        log.info("Refreshing token for tenantName={}", tenantName);

        Tenant tenant = tenantRepository.findByTenantName(tenantName)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantName));

        String realm = tenant.getRealmName();
        String clientId = tenant.getTenantName();

        try {
            String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", refreshToken);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                return TokenRefreshResponse.success(
                        (String) responseBody.get("access_token"),
                        (String) responseBody.get("refresh_token"),
                        ((Number) responseBody.get("expires_in")).intValue(),
                        ((Number) responseBody.get("refresh_expires_in")).intValue()
                );
            }

            return TokenRefreshResponse.failure("Token refresh failed");
        } catch (Exception e) {
            log.error("Token refresh failed for tenantName={}: {}", tenantName, e.getMessage(), e);
            return TokenRefreshResponse.failure("Token refresh failed: " + e.getMessage());
        }
    }

    // ============================================================
    // PASSWORD MANAGEMENT
    // ============================================================

    /**
     * Change password for a user.
     *
     * @param tenantId        the tenant ID
     * @param userId          the user ID
     * @param passwordRequest the password change request
     * @return password change response
     */
    public PasswordChangeResponse changePassword(String tenantId, String userId,
                                                   PasswordChangeRequest passwordRequest) {
        log.info("Changing password for user {} in tenantId={}", userId, tenantId);

        // Validate password confirmation
        if (!passwordRequest.getNewPassword().equals(passwordRequest.getConfirmPassword())) {
            return PasswordChangeResponse.failure("New password and confirmation do not match");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        try {
            // Verify current password by attempting to get a token
            boolean currentPasswordValid = kcUtil.verifyUserPassword(
                    tenant.getRealmName(), userId, passwordRequest.getCurrentPassword());

            if (!currentPasswordValid) {
                return PasswordChangeResponse.failure("Current password is incorrect");
            }

            // Set new password
            kcUtil.setUserPassword(tenant.getRealmName(), userId, passwordRequest.getNewPassword(), false);

            // Log audit event
            loginAuditService.logPasswordChange(tenantId, tenant.getRealmName(), userId, "self");

            log.info("Password changed successfully for user {} in tenantId={}", userId, tenantId);
            return PasswordChangeResponse.success("Password changed successfully");
        } catch (Exception e) {
            log.error("Password change failed for user {} in tenantId={}: {}",
                    userId, tenantId, e.getMessage(), e);
            return PasswordChangeResponse.failure("Password change failed: " + e.getMessage());
        }
    }

    /**
     * Force password reset for a user (admin operation).
     *
     * @param tenantId the tenant ID
     * @param userId   the target user ID
     * @return password change response
     */
    public PasswordChangeResponse forcePasswordReset(String tenantId, String userId) {
        log.info("Forcing password reset for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        try {
            // Set required action for password update
            kcUtil.setRequiredAction(tenant.getRealmName(), userId, "UPDATE_PASSWORD");

            // Log audit event
            loginAuditService.logPasswordReset(tenantId, tenant.getRealmName(), userId, "admin");

            log.info("Password reset required for user {} in tenantId={}", userId, tenantId);
            return PasswordChangeResponse.success("Password reset required on next login", false);
        } catch (Exception e) {
            log.error("Force password reset failed for user {} in tenantId={}: {}",
                    userId, tenantId, e.getMessage(), e);
            return PasswordChangeResponse.failure("Password reset failed: " + e.getMessage());
        }
    }

    // ============================================================
    // SESSION SEARCH
    // ============================================================

    /**
     * Search sessions with filters.
     *
     * @param tenantId      the tenant ID
     * @param searchRequest the search criteria
     * @return session list response with matching sessions
     */
    public SessionListResponse searchSessions(String tenantId, SessionSearchRequest searchRequest) {
        log.info("Searching sessions for tenantId={} with filters", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Get all sessions first (Keycloak doesn't support server-side filtering)
        List<UserSessionRepresentation> allSessions = kcUtil.getAllSessions(
                tenant.getRealmName(), 0, 1000);

        // Apply filters
        List<SessionDTO> filteredSessions = allSessions.stream()
                .map(SessionDTO::fromKeycloakSession)
                .filter(session -> matchesSearchCriteria(session, searchRequest))
                .collect(Collectors.toList());

        // Sort
        if (searchRequest.getSortBy() != null) {
            filteredSessions = sortSessions(filteredSessions, searchRequest.getSortBy(), searchRequest.isSortDesc());
        }

        // Paginate
        int totalCount = filteredSessions.size();
        int fromIndex = searchRequest.getPage() * searchRequest.getSize();
        int toIndex = Math.min(fromIndex + searchRequest.getSize(), totalCount);

        if (fromIndex >= totalCount) {
            return SessionListResponse.of(List.of(), searchRequest.getPage(),
                    searchRequest.getSize(), totalCount);
        }

        List<SessionDTO> pagedSessions = filteredSessions.subList(fromIndex, toIndex);

        return SessionListResponse.of(pagedSessions, searchRequest.getPage(),
                searchRequest.getSize(), totalCount);
    }

    private boolean matchesSearchCriteria(SessionDTO session, SessionSearchRequest request) {
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            if (!request.getUserId().equalsIgnoreCase(session.getUserId())) {
                return false;
            }
        }

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (session.getUsername() == null ||
                    !session.getUsername().toLowerCase().contains(request.getUsername().toLowerCase())) {
                return false;
            }
        }

        if (request.getIpAddress() != null && !request.getIpAddress().isBlank()) {
            if (session.getIpAddress() == null ||
                    !session.getIpAddress().equals(request.getIpAddress())) {
                return false;
            }
        }

        if (request.getRememberMe() != null) {
            if (session.isRememberMe() != request.getRememberMe()) {
                return false;
            }
        }

        if (request.getStartTimeFrom() != null) {
            if (session.getStartTime() == null ||
                    session.getStartTime().isBefore(request.getStartTimeFrom())) {
                return false;
            }
        }

        if (request.getStartTimeTo() != null) {
            if (session.getStartTime() == null ||
                    session.getStartTime().isAfter(request.getStartTimeTo())) {
                return false;
            }
        }

        if (request.getLastAccessFrom() != null) {
            if (session.getLastAccess() == null ||
                    session.getLastAccess().isBefore(request.getLastAccessFrom())) {
                return false;
            }
        }

        if (request.getLastAccessTo() != null) {
            if (session.getLastAccess() == null ||
                    session.getLastAccess().isAfter(request.getLastAccessTo())) {
                return false;
            }
        }

        return true;
    }

    private List<SessionDTO> sortSessions(List<SessionDTO> sessions, String sortBy, boolean desc) {
        return sessions.stream()
                .sorted((s1, s2) -> {
                    int comparison = 0;
                    switch (sortBy.toLowerCase()) {
                        case "starttime":
                            comparison = compareNullable(s1.getStartTime(), s2.getStartTime());
                            break;
                        case "lastaccess":
                            comparison = compareNullable(s1.getLastAccess(), s2.getLastAccess());
                            break;
                        case "username":
                            comparison = compareNullable(s1.getUsername(), s2.getUsername());
                            break;
                        case "ipaddress":
                            comparison = compareNullable(s1.getIpAddress(), s2.getIpAddress());
                            break;
                        default:
                            comparison = compareNullable(s1.getLastAccess(), s2.getLastAccess());
                    }
                    return desc ? -comparison : comparison;
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>> int compareNullable(T a, T b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
    // ============================================================
    // API KEY → JWT TOKEN EXCHANGE
    // ============================================================

    /**
     * Exchange a raw API key for a Keycloak access token.
     *
     * Flow:
     *  1. SHA-256 hash the raw key → look up ExtensionApiKey by keyHash
     *  2. Validate: status=ACTIVE and not expired
     *  3. Decrypt the stored client secret (AES-256)
     *  4. Call Keycloak token endpoint with client_credentials grant
     *  5. Update lastUsedAt and return tenantName + access token
     *
     * @param rawApiKey the plain-text API key sent by the client
     * @return ApiKeyTokenResponse containing tenantName and accessToken on success
     */
    public ApiKeyTokenResponse exchangeApiKeyForToken(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return ApiKeyTokenResponse.failure("API key must not be blank");
        }

        // 1️⃣ Hash → lookup
        String keyHash = ApiKeyUtil.hash(rawApiKey.trim());
        ExtensionApiKey apiKey = apiKeyRepository.findByKeyHash(keyHash)
                .orElse(null);

        if (apiKey == null) {
            log.warn("exchangeApiKeyForToken: unknown API key (prefix={})", rawApiKey.length() > 8 ? rawApiKey.substring(0, 8) : "?");
            return ApiKeyTokenResponse.failure("Invalid API key");
        }

        // 2️⃣ Status check
        if (!"ACTIVE".equalsIgnoreCase(apiKey.getStatus())) {
            log.warn("exchangeApiKeyForToken: key is not active, status={} tenantId={}", apiKey.getStatus(), apiKey.getTenantId());
            return ApiKeyTokenResponse.failure("API key is not active");
        }

        // 3️⃣ Expiry check
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("exchangeApiKeyForToken: key expired at={} tenantId={}", apiKey.getExpiresAt(), apiKey.getTenantId());
            return ApiKeyTokenResponse.failure("API key has expired");
        }

        // 4️⃣ Resolve tenant
        Tenant tenant = tenantRepository.findById(apiKey.getTenantId())
                .orElse(null);

        if (tenant == null) {
            log.error("exchangeApiKeyForToken: tenant not found for tenantId={}", apiKey.getTenantId());
            return ApiKeyTokenResponse.failure("Tenant not found");
        }

        // 5️⃣ Decrypt client secret
        String clientSecret;
        try {
            clientSecret = CryptoUtil.decrypt(apiKey.getClientSecret());
        } catch (Exception e) {
            log.error("exchangeApiKeyForToken: failed to decrypt client secret for tenantId={}: {}", apiKey.getTenantId(), e.getMessage());
            return ApiKeyTokenResponse.failure("Internal error: secret decryption failed");
        }

        // 6️⃣ Call Keycloak token endpoint — client_credentials grant
        try {
            String tokenUrl = keycloakServerUrl + "/realms/" + tenant.getRealmName()
                    + "/protocol/openid-connect/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", apiKey.getClientId());
            body.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("exchangeApiKeyForToken: Keycloak returned status={} for tenantId={}", response.getStatusCode(), apiKey.getTenantId());
                return ApiKeyTokenResponse.failure("Failed to obtain token from Keycloak");
            }

            Map<String, Object> responseBody = response.getBody();

            // 7️⃣ Update lastUsedAt
            apiKey.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(apiKey);

            log.info("exchangeApiKeyForToken: token issued for tenant={} clientId={}", tenant.getTenantName(), apiKey.getClientId());

            return ApiKeyTokenResponse.success(
                    tenant.getTenantName(),
                    (String) responseBody.get("access_token"),
                    (String) responseBody.getOrDefault("token_type", "Bearer"),
                    ((Number) responseBody.getOrDefault("expires_in", 0)).intValue()
            );

        } catch (Exception e) {
            log.error("exchangeApiKeyForToken: token fetch failed for tenantId={}: {}", apiKey.getTenantId(), e.getMessage());
            return ApiKeyTokenResponse.failure("Token exchange failed: " + e.getMessage());
        }
    }

    // ============================================================
    // EXTENDED SESSION MANAGEMENT
    // ============================================================

    /**
     * Find a specific session by its ID within the tenant's realm.
     *
     * @param tenantId  the tenant ID
     * @param sessionId the Keycloak session ID
     * @return the session DTO, or empty if not found
     */
    public Optional<SessionDTO> getSessionById(String tenantId, String sessionId) {
        log.debug("Looking up session {} in tenantId={}", sessionId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        return kcUtil.getSessionById(tenant.getRealmName(), sessionId)
                .map(SessionDTO::fromKeycloakSession);
    }

    /**
     * Get the number of active sessions for a specific user.
     *
     * @param tenantId the tenant ID
     * @param userId   the Keycloak user ID
     * @return number of active sessions
     */
    public int getUserSessionCount(String tenantId, String userId) {
        log.debug("Getting session count for user {} in tenantId={}", userId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        return kcUtil.getUserSessionCount(tenant.getRealmName(), userId);
    }

    /**
     * List all users who have at least one active session, with per-user session info.
     *
     * @param tenantId the tenant ID
     * @return list of active users
     */
    public List<ActiveUserDTO> getActiveUsers(String tenantId) {
        log.info("Fetching active users for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> allSessions = kcUtil.getAllSessions(
                tenant.getRealmName(), 0, 10000);

        // Group sessions by userId
        Map<String, List<UserSessionRepresentation>> byUser = allSessions.stream()
                .filter(s -> s.getUserId() != null)
                .collect(Collectors.groupingBy(UserSessionRepresentation::getUserId));

        return byUser.entrySet().stream()
                .map(entry -> {
                    List<UserSessionRepresentation> userSessions = entry.getValue();
                    UserSessionRepresentation latest = userSessions.stream()
                            .max(Comparator.comparingLong(UserSessionRepresentation::getLastAccess))
                            .orElse(userSessions.get(0));
                    UserSessionRepresentation earliest = userSessions.stream()
                            .min(Comparator.comparingLong(UserSessionRepresentation::getStart))
                            .orElse(userSessions.get(0));

                    LocalDateTime lastAccess = latest.getLastAccess() > 0
                            ? LocalDateTime.ofInstant(Instant.ofEpochMilli(latest.getLastAccess()), ZoneId.systemDefault())
                            : null;
                    LocalDateTime firstLogin = earliest.getStart() > 0
                            ? LocalDateTime.ofInstant(Instant.ofEpochMilli(earliest.getStart()), ZoneId.systemDefault())
                            : null;

                    return ActiveUserDTO.builder()
                            .userId(entry.getKey())
                            .username(latest.getUsername())
                            .ipAddress(latest.getIpAddress())
                            .sessionCount(userSessions.size())
                            .lastAccess(lastAccess)
                            .firstLogin(firstLogin)
                            .build();
                })
                .sorted(Comparator.comparing(ActiveUserDTO::getLastAccess,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Terminate all sessions for a user except the specified current session.
     *
     * @param tenantId         the tenant ID
     * @param userId           the Keycloak user ID
     * @param currentSessionId the session ID to keep alive
     * @return logout response with revoked session details
     */
    public LogoutResponse logoutOtherSessions(String tenantId, String userId, String currentSessionId) {
        log.info("Logging out other sessions for user {} in tenantId={}, keeping session {}",
                userId, tenantId, currentSessionId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> sessions = kcUtil.getUserSessions(tenant.getRealmName(), userId);

        List<String> othersToRevoke = sessions.stream()
                .map(UserSessionRepresentation::getId)
                .filter(id -> !id.equals(currentSessionId))
                .collect(Collectors.toList());

        if (othersToRevoke.isEmpty()) {
            return LogoutResponse.success("No other sessions to revoke", 0);
        }

        return revokeBulkSessions(tenantId, othersToRevoke);
    }

    /**
     * Build a comprehensive session summary for the tenant.
     *
     * @param tenantId the tenant ID
     * @return session summary with counts, per-client breakdown, and avg duration
     */
    public SessionSummaryDTO getSessionSummary(String tenantId) {
        log.debug("Building session summary for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        List<UserSessionRepresentation> allSessions = kcUtil.getAllSessions(
                tenant.getRealmName(), 0, 10000);

        long uniqueUsers = allSessions.stream()
                .map(UserSessionRepresentation::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // Average session duration
        long avgDurationSeconds = 0;
        if (!allSessions.isEmpty()) {
            long now = System.currentTimeMillis();
            OptionalDouble avg = allSessions.stream()
                    .filter(s -> s.getStart() > 0)
                    .mapToLong(s -> (now - s.getStart()) / 1000)
                    .average();
            avgDurationSeconds = avg.isPresent() ? (long) avg.getAsDouble() : 0;
        }

        Map<String, Long> sessionsByClient = kcUtil.getSessionStats(tenant.getRealmName());

        return SessionSummaryDTO.builder()
                .totalActiveSessions(allSessions.size())
                .uniqueActiveUsers((int) uniqueUsers)
                .sessionsByClient(sessionsByClient)
                .avgSessionDurationSeconds(avgDurationSeconds)
                .avgSessionDurationFormatted(formatDuration(avgDurationSeconds))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "0m";
        Duration d = Duration.ofSeconds(seconds);
        long days = d.toDays();
        long hours = d.toHours() % 24;
        long minutes = d.toMinutes() % 60;
        if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }
}
