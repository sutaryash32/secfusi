package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.tenant.service.LoginAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tenants/audit/login")
@RequiredArgsConstructor
@Tag(name = "Login Audit", description = "APIs for login event auditing and security monitoring")
public class LoginAuditController {

    private final LoginAuditService loginAuditService;

    // ============================================================
    // GET AUDIT EVENTS
    // ============================================================

    @Operation(summary = "Get all login audit events",
            description = "Returns all login audit events for the tenant with pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEvents(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getEvents - tenantId={}, page={}, size={}", tenantId, page, size);

        LoginAuditPageResponse response = loginAuditService.getEvents(tenantId, page, size);

        log.info("EXIT getEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events for a user by user ID",
            description = "Returns all login audit events for a specific user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events/user/{userId}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByUser(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID") @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getEventsByUser - tenantId={}, userId={}", tenantId, userId);

        LoginAuditPageResponse response = loginAuditService.getEventsByUser(tenantId, userId, page, size);

        log.info("EXIT getEventsByUser - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events by username",
            description = "Returns all login audit events for a specific username.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events/username/{username}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByUsername(
            HttpServletRequest request,
            @Parameter(description = "Username") @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getEventsByUsername - tenantId={}, username={}", tenantId, username);

        LoginAuditPageResponse response = loginAuditService.getEventsByUsername(tenantId, username, page, size);

        log.info("EXIT getEventsByUsername - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events by event type",
            description = "Returns all login audit events of a specific type (LOGIN_SUCCESS, LOGIN_FAILURE, etc.).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events/type/{eventType}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByType(
            HttpServletRequest request,
            @Parameter(description = "Event type") @PathVariable LoginEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getEventsByType - tenantId={}, eventType={}", tenantId, eventType);

        LoginAuditPageResponse response = loginAuditService.getEventsByType(tenantId, eventType, page, size);

        log.info("EXIT getEventsByType - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events by IP address",
            description = "Returns all login audit events from a specific IP address.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events/ip/{ipAddress}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByIpAddress(
            HttpServletRequest request,
            @Parameter(description = "IP Address") @PathVariable String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getEventsByIpAddress - tenantId={}, ipAddress={}", tenantId, ipAddress);

        LoginAuditPageResponse response = loginAuditService.getEventsByIpAddress(tenantId, ipAddress, page, size);

        log.info("EXIT getEventsByIpAddress - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events within a time range",
            description = "Returns all login audit events within the specified time range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events/timerange")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByTimeRange(
            HttpServletRequest request,
            @Parameter(description = "Start time (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "End time (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getEventsByTimeRange - tenantId={}, start={}, end={}", tenantId, start, end);

        LoginAuditPageResponse response = loginAuditService.getEventsByTimeRange(tenantId, start, end, page, size);

        log.info("EXIT getEventsByTimeRange - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Search login events",
            description = "Search login audit events with multiple criteria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/events/search")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> searchEvents(
            HttpServletRequest request,
            @RequestBody LoginAuditSearchRequest searchRequest
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER searchEvents - tenantId={}", tenantId);

        LoginAuditPageResponse response = loginAuditService.searchEvents(tenantId, searchRequest);

        log.info("EXIT searchEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get events for a session",
            description = "Returns all login audit events associated with a specific session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/events/session/{sessionId}")
    public ResponseEntity<ResponseDto<List<LoginAuditEventDTO>>> getSessionEvents(
            HttpServletRequest request,
            @Parameter(description = "Session ID") @PathVariable String sessionId
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getSessionEvents - tenantId={}, sessionId={}", tenantId, sessionId);

        List<LoginAuditEventDTO> events = loginAuditService.getSessionEvents(tenantId, sessionId);

        log.info("EXIT getSessionEvents - count={}", events.size());
        return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // STATISTICS
    // ============================================================

    @Operation(summary = "Get login statistics",
            description = "Returns login statistics for the tenant within the specified time range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/stats")
    public ResponseEntity<ResponseDto<LoginAuditStatsDTO>> getStats(
            HttpServletRequest request,
            @Parameter(description = "Start time (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "End time (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getStats - tenantId={}, start={}, end={}", tenantId, start, end);

        LoginAuditStatsDTO stats = loginAuditService.getStats(tenantId, start, end);

        log.info("EXIT getStats - totalAttempts={}", stats.getTotalLoginAttempts());
        return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get statistics for the last 24 hours",
            description = "Returns login statistics for the last 24 hours.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/stats/today")
    public ResponseEntity<ResponseDto<LoginAuditStatsDTO>> getTodayStats(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getTodayStats - tenantId={}", tenantId);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(24);
        LoginAuditStatsDTO stats = loginAuditService.getStats(tenantId, start, end);

        log.info("EXIT getTodayStats - totalAttempts={}", stats.getTotalLoginAttempts());
        return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // SECURITY MONITORING
    // ============================================================

    @Operation(summary = "Get failed login attempts for a user",
            description = "Returns the count of failed login attempts for a user in the last N minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/security/failures/user/{username}")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getRecentFailures(
            HttpServletRequest request,
            @Parameter(description = "Username") @PathVariable String username,
            @Parameter(description = "Minutes to look back") @RequestParam(defaultValue = "30") int minutes
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getRecentFailures - tenantId={}, username={}, minutes={}", tenantId, username, minutes);

        long count = loginAuditService.countRecentLoginFailures(tenantId, username, minutes);

        Map<String, Object> result = Map.of(
                "username", username,
                "failedAttempts", count,
                "timeWindowMinutes", minutes
        );

        log.info("EXIT getRecentFailures - count={}", count);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get failed login attempts from an IP",
            description = "Returns the count of failed login attempts from an IP address in the last N minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/security/failures/ip/{ipAddress}")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getRecentFailuresFromIp(
            HttpServletRequest request,
            @Parameter(description = "IP Address") @PathVariable String ipAddress,
            @Parameter(description = "Minutes to look back") @RequestParam(defaultValue = "30") int minutes
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getRecentFailuresFromIp - tenantId={}, ipAddress={}, minutes={}", tenantId, ipAddress, minutes);

        long count = loginAuditService.countRecentLoginFailuresFromIp(tenantId, ipAddress, minutes);

        Map<String, Object> result = Map.of(
                "ipAddress", ipAddress,
                "failedAttempts", count,
                "timeWindowMinutes", minutes
        );

        log.info("EXIT getRecentFailuresFromIp - count={}", count);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Find suspicious IP addresses",
            description = "Returns IP addresses with many failed login attempts (potential brute force attacks).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suspicious IPs retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/security/suspicious-ips")
    public ResponseEntity<ResponseDto<List<Map<String, Object>>>> findSuspiciousIps(
            HttpServletRequest request,
            @Parameter(description = "Minutes to look back") @RequestParam(defaultValue = "60") int minutes,
            @Parameter(description = "Failure threshold") @RequestParam(defaultValue = "5") long threshold
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER findSuspiciousIps - tenantId={}, minutes={}, threshold={}", tenantId, minutes, threshold);

        List<Map<String, Object>> suspiciousIps = loginAuditService.findSuspiciousIps(tenantId, minutes, threshold);

        log.info("EXIT findSuspiciousIps - count={}", suspiciousIps.size());
        return ResponseEntity.ok(new ResponseDto<>(suspiciousIps, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // ADMIN OPERATIONS
    // ============================================================

    @Operation(summary = "Delete old audit events",
            description = "Deletes login audit events older than the specified number of days (Admin only).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Old events deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @DeleteMapping("/events/cleanup")
    public ResponseEntity<ResponseDto<Map<String, Object>>> deleteOldEvents(
            HttpServletRequest request,
            @Parameter(description = "Days to keep") @RequestParam(defaultValue = "90") int daysToKeep
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER deleteOldEvents - tenantId={}, daysToKeep={}", tenantId, daysToKeep);

        loginAuditService.deleteOldEvents(tenantId, daysToKeep);

        Map<String, Object> result = Map.of(
                "message", "Old audit events deleted successfully",
                "daysKept", daysToKeep
        );

        log.info("EXIT deleteOldEvents - completed");
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }
}
