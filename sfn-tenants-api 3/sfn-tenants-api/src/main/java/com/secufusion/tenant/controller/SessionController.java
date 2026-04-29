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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dedicated REST controller for complete session management.
 *
 * Base URL: /api/tenants/sessions
 *
 * All endpoints except where noted require a valid Bearer token.
 * The tenant is resolved automatically from the authenticated user
 * via the 'loggedInUser' request attribute set by the JWT filter.
 */
@Slf4j
@RestController
@RequestMapping("/api/tenants/sessions")
@RequiredArgsConstructor
@Tag(name = "Session Management", description = "Complete session lifecycle management — view, search, and terminate sessions")
public class SessionController {

    private final AuthService authService;

    // ============================================================
    // LIST / QUERY — ALL SESSIONS
    // ============================================================

    @Operation(
            summary = "List all active sessions (paginated)",
            description = "Returns all active sessions in the tenant's Keycloak realm, ordered by last access descending.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<ResponseDto<SessionListResponse>> getAllSessions(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)")  @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getAllSessions - tenantId={}, page={}, size={}", user.getTenantId(), page, size);

        SessionListResponse response = authService.getAllSessions(user.getTenantId(), page, size);

        log.info("EXIT getAllSessions - totalCount={}", response.getTotalCount());
        return ok(response);
    }

    @Operation(
            summary = "Get total active session count",
            description = "Returns the total number of active sessions across all clients in the tenant realm.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/count")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getSessionCount(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getSessionCount - tenantId={}", user.getTenantId());

        int count = authService.getActiveSessionCount(user.getTenantId());
        Map<String, Object> result = Map.of("totalActiveSessions", count);

        log.info("EXIT getSessionCount - count={}", count);
        return ok(result);
    }

    @Operation(
            summary = "Get session summary",
            description = "Returns a comprehensive overview: total sessions, unique active users, average session duration, and per-client breakdown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/summary")
    public ResponseEntity<ResponseDto<SessionSummaryDTO>> getSessionSummary(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getSessionSummary - tenantId={}", user.getTenantId());

        SessionSummaryDTO summary = authService.getSessionSummary(user.getTenantId());

        log.info("EXIT getSessionSummary - totalSessions={}, uniqueUsers={}",
                summary.getTotalActiveSessions(), summary.getUniqueActiveUsers());
        return ok(summary);
    }

    @Operation(
            summary = "Get session statistics by client",
            description = "Returns the active session count for each Keycloak client in the tenant realm.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/stats")
    public ResponseEntity<ResponseDto<Map<String, Long>>> getSessionStats(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getSessionStats - tenantId={}", user.getTenantId());

        Map<String, Long> stats = authService.getSessionStats(user.getTenantId());

        log.info("EXIT getSessionStats - clients={}", stats.size());
        return ok(stats);
    }

    @Operation(
            summary = "Get a specific session by ID",
            description = "Returns the full details of a single active session. Returns 404 if the session does not exist or has already expired.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session found"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{sessionId}")
    public ResponseEntity<ResponseDto<SessionDTO>> getSessionById(
            HttpServletRequest request,
            @Parameter(description = "Keycloak session ID") @PathVariable String sessionId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getSessionById - tenantId={}, sessionId={}", user.getTenantId(), sessionId);

        return authService.getSessionById(user.getTenantId(), sessionId)
                .map(session -> {
                    log.info("EXIT getSessionById - found session for user={}", session.getUsername());
                    return ok(session);
                })
                .orElseGet(() -> {
                    log.info("EXIT getSessionById - session not found: {}", sessionId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ResponseDto<>(null, "404"));
                });
    }

    // ============================================================
    // ACTIVE USERS
    // ============================================================

    @Operation(
            summary = "List users with active sessions",
            description = "Returns every user who has at least one active session, with session count and last-access time. Sorted by most recently active first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active users retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/users")
    public ResponseEntity<ResponseDto<List<ActiveUserDTO>>> getActiveUsers(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getActiveUsers - tenantId={}", user.getTenantId());

        List<ActiveUserDTO> activeUsers = authService.getActiveUsers(user.getTenantId());

        log.info("EXIT getActiveUsers - uniqueUsers={}", activeUsers.size());
        return ok(activeUsers);
    }

    // ============================================================
    // CURRENT USER — SELF-SERVICE
    // ============================================================

    @Operation(
            summary = "Get my active sessions",
            description = "Returns all active sessions for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    public ResponseEntity<ResponseDto<SessionListResponse>> getMySessions(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getMySessions - tenantId={}, keycloakUserId={}", user.getTenantId(), user.getKeycloakUserId());

        SessionListResponse response = authService.getUserSessionsDTO(user.getTenantId(), user.getKeycloakUserId());

        log.info("EXIT getMySessions - count={}", response.getTotalCount());
        return ok(response);
    }

    @Operation(
            summary = "Get my session count",
            description = "Returns the number of active sessions for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me/count")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getMySessionCount(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getMySessionCount - tenantId={}, keycloakUserId={}", user.getTenantId(), user.getKeycloakUserId());

        int count = authService.getUserSessionCount(user.getTenantId(), user.getKeycloakUserId());
        Map<String, Object> result = Map.of(
                "userId", user.getKeycloakUserId(),
                "username", user.getUsername(),
                "sessionCount", count
        );

        log.info("EXIT getMySessionCount - count={}", count);
        return ok(result);
    }

    @Operation(
            summary = "Terminate current session only",
            description = "Revokes only the session used for this request. All other sessions remain active.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current session terminated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/me/current")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateCurrentSession(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateCurrentSession - tenantId={}, sessionId={}", user.getTenantId(), user.getSessionId());

        LogoutResponse response = authService.revokeSession(user.getTenantId(), user.getSessionId());

        log.info("EXIT terminateCurrentSession - success={}", response.isSuccess());
        return ok(response);
    }

    @Operation(
            summary = "Terminate all my other sessions",
            description = "Revokes every active session for the current user EXCEPT the session making this request. Useful for 'sign out everywhere else'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Other sessions terminated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/me/others")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateOtherSessions(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateOtherSessions - tenantId={}, keycloakUserId={}, currentSessionId={}",
                user.getTenantId(), user.getKeycloakUserId(), user.getSessionId());

        LogoutResponse response = authService.logoutOtherSessions(
                user.getTenantId(), user.getKeycloakUserId(), user.getSessionId());

        log.info("EXIT terminateOtherSessions - sessionsRevoked={}", response.getSessionsRevoked());
        return ok(response);
    }

    @Operation(
            summary = "Terminate all my sessions",
            description = "Revokes all active sessions for the current user, including the current one. The user will be fully logged out.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All sessions terminated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/me")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateAllMySessions(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateAllMySessions - tenantId={}, keycloakUserId={}", user.getTenantId(), user.getKeycloakUserId());

        LogoutResponse response = authService.logoutUser(user.getTenantId(), user.getKeycloakUserId());

        log.info("EXIT terminateAllMySessions - sessionsRevoked={}", response.getSessionsRevoked());
        return ok(response);
    }

    // ============================================================
    // BY USER (Admin)
    // ============================================================

    @Operation(
            summary = "Get sessions for a specific user",
            description = "Returns all active sessions for the given Keycloak user ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<ResponseDto<SessionListResponse>> getUserSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak user ID") @PathVariable String userId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getUserSessions - tenantId={}, targetUserId={}", user.getTenantId(), userId);

        SessionListResponse response = authService.getUserSessionsDTO(user.getTenantId(), userId);

        log.info("EXIT getUserSessions - count={}", response.getTotalCount());
        return ok(response);
    }

    @Operation(
            summary = "Get session count for a specific user",
            description = "Returns the number of active sessions for the given Keycloak user ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/user/{userId}/count")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getUserSessionCount(
            HttpServletRequest request,
            @Parameter(description = "Keycloak user ID") @PathVariable String userId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getUserSessionCount - tenantId={}, targetUserId={}", user.getTenantId(), userId);

        int count = authService.getUserSessionCount(user.getTenantId(), userId);
        Map<String, Object> result = Map.of("userId", userId, "sessionCount", count);

        log.info("EXIT getUserSessionCount - count={}", count);
        return ok(result);
    }

    @Operation(
            summary = "Terminate all sessions for a user (Admin)",
            description = "Revokes every active session for the specified user. Requires admin privileges.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User sessions terminated"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateUserSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak user ID") @PathVariable String userId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateUserSessions - tenantId={}, targetUserId={}", user.getTenantId(), userId);

        LogoutResponse response = authService.logoutUser(user.getTenantId(), userId);

        log.info("EXIT terminateUserSessions - sessionsRevoked={}", response.getSessionsRevoked());
        return ok(response);
    }

    // ============================================================
    // BY CLIENT
    // ============================================================

    @Operation(
            summary = "Get sessions for a Keycloak client",
            description = "Returns all active sessions associated with a specific Keycloak client ID (application).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Client not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/client/{clientId}")
    public ResponseEntity<ResponseDto<SessionListResponse>> getClientSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak client ID (e.g. mycompany)") @PathVariable String clientId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getClientSessions - tenantId={}, clientId={}", user.getTenantId(), clientId);

        SessionListResponse response = authService.getClientSessions(user.getTenantId(), clientId);

        log.info("EXIT getClientSessions - count={}", response.getTotalCount());
        return ok(response);
    }

    // ============================================================
    // OFFLINE SESSIONS (remember-me)
    // ============================================================

    @Operation(
            summary = "Get offline sessions for a user",
            description = "Returns offline (remember-me) sessions for the specified user. These persist beyond the browser session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offline sessions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/offline/user/{userId}")
    public ResponseEntity<ResponseDto<SessionListResponse>> getUserOfflineSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak user ID") @PathVariable String userId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER getUserOfflineSessions - tenantId={}, targetUserId={}", user.getTenantId(), userId);

        SessionListResponse response = authService.getUserOfflineSessions(user.getTenantId(), userId);

        log.info("EXIT getUserOfflineSessions - count={}", response.getTotalCount());
        return ok(response);
    }

    @Operation(
            summary = "Revoke offline sessions for a user",
            description = "Revokes all offline (remember-me) sessions for the specified user, requiring them to log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offline sessions revoked"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/offline/user/{userId}")
    public ResponseEntity<ResponseDto<LogoutResponse>> revokeUserOfflineSessions(
            HttpServletRequest request,
            @Parameter(description = "Keycloak user ID") @PathVariable String userId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER revokeUserOfflineSessions - tenantId={}, targetUserId={}", user.getTenantId(), userId);

        LogoutResponse response = authService.revokeUserOfflineSessions(user.getTenantId(), userId);

        log.info("EXIT revokeUserOfflineSessions - success={}", response.isSuccess());
        return ok(response);
    }

    // ============================================================
    // SEARCH & FILTER
    // ============================================================

    @Operation(
            summary = "Search and filter sessions",
            description = "Search active sessions by user ID, username, IP address, client, time range, and remember-me flag. Results are paginated and sortable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search criteria"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/search")
    public ResponseEntity<ResponseDto<SessionListResponse>> searchSessions(
            HttpServletRequest request,
            @Valid @RequestBody SessionSearchRequest searchRequest
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER searchSessions - tenantId={}", user.getTenantId());

        SessionListResponse response = authService.searchSessions(user.getTenantId(), searchRequest);

        log.info("EXIT searchSessions - count={}", response.getTotalCount());
        return ok(response);
    }

    // ============================================================
    // TERMINATE — INDIVIDUAL / BULK / ALL
    // ============================================================

    @Operation(
            summary = "Terminate a specific session",
            description = "Revokes a single session identified by its session ID. The affected user will be logged out of that session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session terminated"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateSession(
            HttpServletRequest request,
            @Parameter(description = "Keycloak session ID") @PathVariable String sessionId
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateSession - tenantId={}, sessionId={}", user.getTenantId(), sessionId);

        LogoutResponse response = authService.revokeSession(user.getTenantId(), sessionId);

        log.info("EXIT terminateSession - success={}", response.isSuccess());
        return ok(response);
    }

    @Operation(
            summary = "Terminate multiple sessions (bulk)",
            description = "Revokes up to 100 sessions in a single request. Returns per-session revoke/fail details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk operation completed (check revokedSessionIds / failedSessionIds for details)"),
            @ApiResponse(responseCode = "400", description = "Invalid request — list empty or exceeds 100"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/bulk")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateBulkSessions(
            HttpServletRequest request,
            @Valid @RequestBody BulkSessionRevokeRequest revokeRequest
    ) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateBulkSessions - tenantId={}, count={}",
                user.getTenantId(), revokeRequest.getSessionIds().size());

        LogoutResponse response = authService.revokeBulkSessions(
                user.getTenantId(), revokeRequest.getSessionIds());

        log.info("EXIT terminateBulkSessions - revoked={}, failed={}",
                response.getSessionsRevoked(), response.getFailedSessionIds().size());
        return ok(response);
    }

    @Operation(
            summary = "Terminate ALL sessions in the tenant (Admin)",
            description = "Revokes every active session in the tenant realm. All users are immediately logged out. Use with extreme caution.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All sessions terminated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden — admin privileges required")
    })
    @DeleteMapping("/all")
    public ResponseEntity<ResponseDto<LogoutResponse>> terminateAllSessions(HttpServletRequest request) {
        LoggedInUserDetailsBean user = loggedIn(request);
        log.info("ENTER terminateAllSessions - tenantId={}", user.getTenantId());

        LogoutResponse response = authService.logoutAllUsers(user.getTenantId());

        log.info("EXIT terminateAllSessions - sessionsRevoked={}", response.getSessionsRevoked());
        return ok(response);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private LoggedInUserDetailsBean loggedIn(HttpServletRequest request) {
        return (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
    }

    private <T> ResponseEntity<ResponseDto<T>> ok(T data) {
        return ResponseEntity.ok(new ResponseDto<>(data, String.valueOf(HttpStatus.OK.value())));
    }
}
