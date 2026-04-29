package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tenants/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Authentication", description = "APIs for logout, token revocation, and session management")
public class AuthController {

    private final AuthService authService;

    /* ==========================================================
                       LOGOUT - Current User
       ========================================================== */

    @Operation(summary = "Logout current user",
            description = "Revokes the provided refresh token, effectively logging out the current user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout")
    public ResponseEntity<ResponseDto<LogoutResponse>> logout(
            HttpServletRequest request,
            @Valid @RequestBody LogoutRequest logoutRequest
    ) {
        String tenantId;
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");

        if (loggedInUser != null) {
            tenantId = loggedInUser.getTenantId();
        } else if (logoutRequest.getTenantName() != null && !logoutRequest.getTenantName().isBlank()) {
            tenantId = authService.resolveTenantIdByName(logoutRequest.getTenantName());
        } else {
            return ResponseEntity.badRequest().body(
                    new ResponseDto<>(LogoutResponse.failure("Tenant name is required when not authenticated"),
                            String.valueOf(HttpStatus.BAD_REQUEST.value())));
        }

        log.info("ENTER logout - tenantId={}", tenantId);

        LogoutResponse response = authService.logout(tenantId, logoutRequest.getRefreshToken());

        log.info("EXIT logout - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       LOGOUT - Specific User (Admin)
       ========================================================== */

    @Operation(summary = "Logout a specific user by user ID (Admin)",
            description = "Revokes all sessions for a specific user. Requires admin privileges.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User logged out successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout/user/{userId}")
    public ResponseEntity<ResponseDto<LogoutResponse>> logoutUser(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID")
            @PathVariable @NotBlank @Size(max = 100) String userId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER logoutUser - tenantId={}, userId={}", tenantId, userId);

        LogoutResponse response = authService.logoutUser(tenantId, userId);

        log.info("EXIT logoutUser - success={}, sessionsRevoked={}", response.isSuccess(), response.getSessionsRevoked());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       LOGOUT - By Username (Admin)
       ========================================================== */

    @Operation(summary = "Logout a user by username (Admin)",
            description = "Revokes all sessions for a user identified by username/email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User logged out successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout/username/{username}")
    public ResponseEntity<ResponseDto<LogoutResponse>> logoutUserByUsername(
            HttpServletRequest request,
            @Parameter(description = "Username or email")
            @PathVariable @NotBlank @Size(max = 255) String username
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER logoutUserByUsername - tenantId={}, username={}", tenantId, username);

        LogoutResponse response = authService.logoutUserByUsername(tenantId, username);

        log.info("EXIT logoutUserByUsername - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       LOGOUT - All Users (Admin)
       ========================================================== */

    @Operation(summary = "Logout all users from tenant (Admin)",
            description = "Revokes all active sessions in the tenant realm. Use with caution.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All users logged out successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin privileges")
    })
    @PostMapping("/logout/all")
    public ResponseEntity<ResponseDto<LogoutResponse>> logoutAllUsers(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER logoutAllUsers - tenantId={}", tenantId);

        LogoutResponse response = authService.logoutAllUsers(tenantId);

        log.info("EXIT logoutAllUsers - success={}, sessionsRevoked={}", response.isSuccess(), response.getSessionsRevoked());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       REVOKE SESSION
       ========================================================== */

    @Operation(summary = "Revoke a specific session",
            description = "Terminates a specific session by session ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked successfully"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<LogoutResponse>> revokeSession(
            HttpServletRequest request,
            @Parameter(description = "Session ID to revoke")
            @PathVariable @NotBlank @Size(max = 100) String sessionId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER revokeSession - tenantId={}, sessionId={}", tenantId, sessionId);

        LogoutResponse response = authService.revokeSession(tenantId, sessionId);

        log.info("EXIT revokeSession - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       GET USER SESSIONS
       ========================================================== */

    @Operation(summary = "Get active sessions for a user",
            description = "Returns all active sessions for a specific user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/user/{userId}")
    public ResponseEntity<ResponseDto<List<UserSessionRepresentation>>> getUserSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID")
            @PathVariable @NotBlank @Size(max = 100) String userId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getUserSessions - tenantId={}, userId={}", tenantId, userId);

        List<UserSessionRepresentation> sessions = authService.getUserSessions(tenantId, userId);

        log.info("EXIT getUserSessions - count={}", sessions.size());
        return ResponseEntity.ok(new ResponseDto<>(sessions, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       GET SESSION COUNT
       ========================================================== */

    @Operation(summary = "Get active session count for tenant",
            description = "Returns the total number of active sessions in the tenant realm.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/count")
    public ResponseEntity<ResponseDto<Integer>> getActiveSessionCount(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getActiveSessionCount - tenantId={}", tenantId);

        int count = authService.getActiveSessionCount(tenantId);

        log.info("EXIT getActiveSessionCount - count={}", count);
        return ResponseEntity.ok(new ResponseDto<>(count, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       SESSION MANAGEMENT
       ========================================================== */

    @Operation(summary = "Get all active sessions (paginated)",
            description = "Returns all active sessions in the tenant realm with pagination support.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions")
    public ResponseEntity<ResponseDto<SessionListResponse>> getAllSessions(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getAllSessions - tenantId={}, page={}, size={}", tenantId, page, size);

        SessionListResponse response = authService.getAllSessions(tenantId, page, size);

        log.info("EXIT getAllSessions - count={}", response.getTotalCount());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get user sessions (detailed)",
            description = "Returns all active sessions for a specific user with detailed information.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/user/{userId}/details")
    public ResponseEntity<ResponseDto<SessionListResponse>> getUserSessionsDetailed(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID")
            @PathVariable @NotBlank @Size(max = 100) String userId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getUserSessionsDetailed - tenantId={}, userId={}", tenantId, userId);

        SessionListResponse response = authService.getUserSessionsDTO(tenantId, userId);

        log.info("EXIT getUserSessionsDetailed - count={}", response.getTotalCount());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get sessions for a client",
            description = "Returns all active sessions for a specific client/application.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Client not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/client/{clientId}")
    public ResponseEntity<ResponseDto<SessionListResponse>> getClientSessions(
            HttpServletRequest request,
            @Parameter(description = "Client ID")
            @PathVariable @NotBlank @Size(max = 100) String clientId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getClientSessions - tenantId={}, clientId={}", tenantId, clientId);

        SessionListResponse response = authService.getClientSessions(tenantId, clientId);

        log.info("EXIT getClientSessions - count={}", response.getTotalCount());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get session statistics",
            description = "Returns session count per client for the tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/stats")
    public ResponseEntity<ResponseDto<Map<String, Long>>> getSessionStats(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getSessionStats - tenantId={}", tenantId);

        Map<String, Long> stats = authService.getSessionStats(tenantId);

        log.info("EXIT getSessionStats - clients={}", stats.size());
        return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get user offline sessions (remember-me)",
            description = "Returns offline sessions for a user (sessions persisted with remember-me).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offline sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/user/{userId}/offline")
    public ResponseEntity<ResponseDto<SessionListResponse>> getUserOfflineSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID")
            @PathVariable @NotBlank @Size(max = 100) String userId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getUserOfflineSessions - tenantId={}, userId={}", tenantId, userId);

        SessionListResponse response = authService.getUserOfflineSessions(tenantId, userId);

        log.info("EXIT getUserOfflineSessions - count={}", response.getTotalCount());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Revoke user offline sessions",
            description = "Revokes all offline sessions (remember-me tokens) for a user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offline sessions revoked successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/sessions/user/{userId}/offline")
    public ResponseEntity<ResponseDto<LogoutResponse>> revokeUserOfflineSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID")
            @PathVariable @NotBlank @Size(max = 100) String userId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER revokeUserOfflineSessions - tenantId={}, userId={}", tenantId, userId);

        LogoutResponse response = authService.revokeUserOfflineSessions(tenantId, userId);

        log.info("EXIT revokeUserOfflineSessions - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       CURRENT USER SESSIONS
       ========================================================== */

    @Operation(summary = "Get current user's sessions",
            description = "Returns all active sessions for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/sessions/me")
    public ResponseEntity<ResponseDto<SessionListResponse>> getCurrentUserSessions(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();
        String userId = loggedInUser.getUserId();

        log.info("ENTER getCurrentUserSessions - tenantId={}, userId={}", tenantId, userId);

        SessionListResponse response = authService.getUserSessionsDTO(tenantId, userId);

        log.info("EXIT getCurrentUserSessions - count={}", response.getTotalCount());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Logout current session only",
            description = "Revokes only the current session, keeping other sessions active.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current session revoked successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/sessions/current")
    public ResponseEntity<ResponseDto<LogoutResponse>> logoutCurrentSession(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();
        String sessionId = loggedInUser.getSessionId();

        log.info("ENTER logoutCurrentSession - tenantId={}, sessionId={}", tenantId, sessionId);

        LogoutResponse response = authService.revokeSession(tenantId, sessionId);

        log.info("EXIT logoutCurrentSession - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       BULK SESSION REVOCATION
       ========================================================== */

    @Operation(summary = "Revoke multiple sessions (bulk)",
            description = "Revokes multiple sessions by their IDs in a single operation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions revoked successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/sessions/bulk")
    public ResponseEntity<ResponseDto<LogoutResponse>> revokeBulkSessions(
            HttpServletRequest request,
            @Valid @RequestBody BulkSessionRevokeRequest revokeRequest
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER revokeBulkSessions - tenantId={}, sessionCount={}",
                tenantId, revokeRequest.getSessionIds().size());

        LogoutResponse response = authService.revokeBulkSessions(tenantId, revokeRequest.getSessionIds());

        log.info("EXIT revokeBulkSessions - success={}, revoked={}",
                response.isSuccess(), response.getSessionsRevoked());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       TOKEN OPERATIONS
       ========================================================== */

    @Operation(summary = "Introspect token",
            description = "Validates and returns information about the provided token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token introspection successful"),
            @ApiResponse(responseCode = "400", description = "Invalid token"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/token/introspect")
    public ResponseEntity<ResponseDto<TokenIntrospectionResponse>> introspectToken(
            HttpServletRequest request,
            @Valid @RequestBody TokenIntrospectionRequest introspectionRequest
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER introspectToken - tenantId={}", tenantId);

        TokenIntrospectionResponse response = authService.introspectToken(
                tenantId, introspectionRequest.getToken());

        log.info("EXIT introspectToken - active={}", response.isActive());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Refresh access token",
            description = "Exchanges a refresh token for a new access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refresh successful"),
            @ApiResponse(responseCode = "400", description = "Invalid refresh token"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/token/refresh")
    public ResponseEntity<ResponseDto<TokenRefreshResponse>> refreshToken(
            @Valid @RequestBody TokenRefreshRequest refreshRequest
    ) {
        log.info("ENTER refreshToken - tenantName={}", refreshRequest.getTenantName());

        TokenRefreshResponse response = authService.refreshToken(
                refreshRequest.getTenantName(), refreshRequest.getRefreshToken());

        log.info("EXIT refreshToken - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       PASSWORD MANAGEMENT
       ========================================================== */

    @Operation(summary = "Change password",
            description = "Changes the password for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password or validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/password/change")
    public ResponseEntity<ResponseDto<PasswordChangeResponse>> changePassword(
            HttpServletRequest request,
            @Valid @RequestBody PasswordChangeRequest passwordRequest
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();
        String userId = loggedInUser.getUserId();

        log.info("ENTER changePassword - tenantId={}, userId={}", tenantId, userId);

        PasswordChangeResponse response = authService.changePassword(
                tenantId, userId, passwordRequest);

        log.info("EXIT changePassword - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Force password reset (Admin)",
            description = "Forces a password reset for a specific user. User will be required to change password on next login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset initiated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires admin privileges")
    })
    @PostMapping("/users/{userId}/password/reset")
    public ResponseEntity<ResponseDto<PasswordChangeResponse>> forcePasswordReset(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID")
            @PathVariable @NotBlank @Size(max = 100) String userId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER forcePasswordReset - tenantId={}, targetUserId={}", tenantId, userId);

        PasswordChangeResponse response = authService.forcePasswordReset(tenantId, userId);

        log.info("EXIT forcePasswordReset - success={}", response.isSuccess());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                       SESSION SEARCH & FILTER
       ========================================================== */

    @Operation(summary = "Search sessions",
            description = "Search and filter sessions by various criteria like IP, device, time range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/sessions/search")
    public ResponseEntity<ResponseDto<SessionListResponse>> searchSessions(
            HttpServletRequest request,
            @Valid @RequestBody SessionSearchRequest searchRequest
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER searchSessions - tenantId={}", tenantId);

        SessionListResponse response = authService.searchSessions(tenantId, searchRequest);

        log.info("EXIT searchSessions - count={}", response.getTotalCount());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    /* ==========================================================
                   API KEY → JWT TOKEN EXCHANGE
       ========================================================== */

    @Operation(
            summary = "Exchange API key for a JWT access token",
            description = "Accepts a raw API key via the `Authorization: ApiKey <key>` header " +
                    "or the `apiKey` field in the request body. " +
                    "Validates the key, then calls Keycloak using the client_credentials grant " +
                    "for the associated extension client and returns an access token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired or inactive API key")
    })
    @PostMapping("/token/api-key")
    public ResponseEntity<ResponseDto<ApiKeyTokenResponse>> exchangeApiKeyForToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body
    ) {
        // Resolve raw key: prefer Authorization header, fall back to body field
        String rawApiKey = null;

        if (authHeader != null && authHeader.startsWith("ApiKey ")) {
            rawApiKey = authHeader.substring("ApiKey ".length()).trim();
        } else if (body != null) {
            rawApiKey = body.get("apiKey");
        }

        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.warn("exchangeApiKeyForToken: no API key provided");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseDto<>(ApiKeyTokenResponse.failure("API key is required"), "401"));
        }

        log.info("ENTER exchangeApiKeyForToken - keyPrefix={}", rawApiKey.length() > 8 ? rawApiKey.substring(0, 8) : "?");

        ApiKeyTokenResponse result = authService.exchangeApiKeyForToken(rawApiKey);

        if (!result.isSuccess()) {
            log.warn("EXIT exchangeApiKeyForToken - failed: {}", result.getError());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseDto<>(result, "401"));
        }

        log.info("EXIT exchangeApiKeyForToken - token issued for tenant={}", result.getTenantName());
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }
}
