package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.iam.entity.LoginAuditEvent.SourceService;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.UserDeviceLogin.DeviceLoginStatus;
import com.secufusion.iam.service.LoginAuditService;
import com.secufusion.iam.util.JwtUtl;
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
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/audit/login")
@RequiredArgsConstructor
@Tag(name = "Login Audit", description = "APIs for login event auditing and security monitoring")
public class LoginAuditController {

    private final LoginAuditService loginAuditService;
    private final JwtUtl jwtUtl;

    private String getTenantId(HttpServletRequest request) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        return tenant != null ? tenant.getTenantID() : null;
    }

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
        String tenantId = getTenantId(request);
        log.info("ENTER getEvents - tenantId={}, page={}, size={}", tenantId, page, size);

        LoginAuditPageResponse response = loginAuditService.getEvents(tenantId, page, size);

        log.info("EXIT getEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events for a user by user ID",
            description = "Returns all login audit events for a specific user.")
    @GetMapping("/events/user/{userId}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByUser(
            HttpServletRequest request,
            @Parameter(description = "Keycloak User ID") @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getEventsByUser - tenantId={}, userId={}", tenantId, userId);

        LoginAuditPageResponse response = loginAuditService.getEventsByUser(tenantId, userId, page, size);

        log.info("EXIT getEventsByUser - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events by event type",
            description = "Returns all login audit events of a specific type.")
    @GetMapping("/events/type/{eventType}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsByType(
            HttpServletRequest request,
            @Parameter(description = "Event type") @PathVariable LoginEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getEventsByType - tenantId={}, eventType={}", tenantId, eventType);

        LoginAuditPageResponse response = loginAuditService.getEventsByType(tenantId, eventType, page, size);

        log.info("EXIT getEventsByType - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get events by source service",
            description = "Returns all audit events from a specific microservice.")
    @GetMapping("/events/source/{sourceService}")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getEventsBySourceService(
            HttpServletRequest request,
            @Parameter(description = "Source Service") @PathVariable SourceService sourceService,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getEventsBySourceService - tenantId={}, sourceService={}", tenantId, sourceService);

        LoginAuditPageResponse response = loginAuditService.getEventsBySourceService(tenantId, sourceService, page, size);

        log.info("EXIT getEventsBySourceService - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events within a time range",
            description = "Returns all login audit events within the specified time range.")
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
        String tenantId = getTenantId(request);
        log.info("ENTER getEventsByTimeRange - tenantId={}, start={}, end={}", tenantId, start, end);

        LoginAuditPageResponse response = loginAuditService.getEventsByTimeRange(tenantId, start, end, page, size);

        log.info("EXIT getEventsByTimeRange - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get events for a session",
            description = "Returns all login audit events associated with a specific session.")
    @GetMapping("/events/session/{sessionId}")
    public ResponseEntity<ResponseDto<List<LoginAuditEventDTO>>> getSessionEvents(
            HttpServletRequest request,
            @Parameter(description = "Session ID") @PathVariable String sessionId
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getSessionEvents - tenantId={}, sessionId={}", tenantId, sessionId);

        List<LoginAuditEventDTO> events = loginAuditService.getSessionEvents(tenantId, sessionId);

        log.info("EXIT getSessionEvents - count={}", events.size());
        return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // IAM-SPECIFIC EVENT QUERIES
    // ============================================================

    @Operation(summary = "Get user management events",
            description = "Returns user creation, update, deletion events within time range.")
    @GetMapping("/events/users")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getUserManagementEvents(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getUserManagementEvents - tenantId={}", tenantId);

        LoginAuditPageResponse response = loginAuditService.getUserManagementEvents(tenantId, start, end, page, size);

        log.info("EXIT getUserManagementEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get role management events",
            description = "Returns role creation, assignment, removal events within time range.")
    @GetMapping("/events/roles")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getRoleManagementEvents(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getRoleManagementEvents - tenantId={}", tenantId);

        LoginAuditPageResponse response = loginAuditService.getRoleManagementEvents(tenantId, start, end, page, size);

        log.info("EXIT getRoleManagementEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get group management events",
            description = "Returns group creation, membership change events within time range.")
    @GetMapping("/events/groups")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getGroupManagementEvents(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getGroupManagementEvents - tenantId={}", tenantId);

        LoginAuditPageResponse response = loginAuditService.getGroupManagementEvents(tenantId, start, end, page, size);

        log.info("EXIT getGroupManagementEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // STATISTICS
    // ============================================================

    @Operation(summary = "Get event counts by source service",
            description = "Returns event counts grouped by source microservice.")
    @GetMapping("/stats/by-source")
    public ResponseEntity<ResponseDto<Map<String, Long>>> getStatsBySourceService(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getStatsBySourceService - tenantId={}", tenantId);

        Map<String, Long> stats = loginAuditService.getEventCountsBySourceService(tenantId, start, end);

        log.info("EXIT getStatsBySourceService - sources={}", stats.size());
        return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // ADMIN OPERATIONS
    // ============================================================

    @Operation(summary = "Delete old audit events",
            description = "Deletes login audit events older than the specified number of days.")
    @DeleteMapping("/events/cleanup")
    public ResponseEntity<ResponseDto<Map<String, Object>>> deleteOldEvents(
            HttpServletRequest request,
            @Parameter(description = "Days to keep") @RequestParam(defaultValue = "90") int daysToKeep
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER deleteOldEvents - tenantId={}, daysToKeep={}", tenantId, daysToKeep);

        loginAuditService.deleteOldEvents(tenantId, daysToKeep);

        Map<String, Object> result = Map.of(
                "message", "Old audit events deleted successfully",
                "daysKept", daysToKeep
        );

        log.info("EXIT deleteOldEvents - completed");
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }

    // ============================================================
    // DEVICE LOGIN TRACKING ENDPOINTS
    // ============================================================

    @Operation(summary = "Get all devices for a user",
            description = "Returns all devices that a user has logged in from.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/devices/user/{userId}")
    public ResponseEntity<ResponseDto<org.springframework.data.domain.Page<LoginDeviceDTO>>> getUserDevices(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getUserDevices - tenantId={}, userId={}", tenantId, userId);

        var devices = loginAuditService.getUserDevices(tenantId, userId, page, size);

        log.info("EXIT getUserDevices - totalElements={}", devices.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get active devices for a user",
            description = "Returns only active devices for a user.")
    @GetMapping("/devices/user/{userId}/active")
    public ResponseEntity<ResponseDto<org.springframework.data.domain.Page<LoginDeviceDTO>>> getActiveUserDevices(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getActiveUserDevices - tenantId={}, userId={}", tenantId, userId);

        var devices = loginAuditService.getActiveUserDevices(tenantId, userId, page, size);

        log.info("EXIT getActiveUserDevices - totalElements={}", devices.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get trusted devices for a user",
            description = "Returns all trusted devices for a user.")
    @GetMapping("/devices/user/{userId}/trusted")
    public ResponseEntity<ResponseDto<List<LoginDeviceDTO>>> getTrustedDevices(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getTrustedDevices - tenantId={}, userId={}", tenantId, userId);

        List<LoginDeviceDTO> devices = loginAuditService.getTrustedDevices(tenantId, userId);

        log.info("EXIT getTrustedDevices - count={}", devices.size());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get all devices for the tenant",
            description = "Returns all devices across all users in the tenant.")
    @GetMapping("/devices")
    public ResponseEntity<ResponseDto<org.springframework.data.domain.Page<LoginDeviceDTO>>> getTenantDevices(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getTenantDevices - tenantId={}", tenantId);

        var devices = loginAuditService.getTenantDevices(tenantId, page, size);

        log.info("EXIT getTenantDevices - totalElements={}", devices.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get devices by status",
            description = "Returns devices filtered by status (ACTIVE, INACTIVE, BLOCKED, etc.).")
    @GetMapping("/devices/status/{status}")
    public ResponseEntity<ResponseDto<org.springframework.data.domain.Page<LoginDeviceDTO>>> getDevicesByStatus(
            HttpServletRequest request,
            @Parameter(description = "Device status") @PathVariable DeviceLoginStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getDevicesByStatus - tenantId={}, status={}", tenantId, status);

        var devices = loginAuditService.getTenantDevicesByStatus(tenantId, status, page, size);

        log.info("EXIT getDevicesByStatus - totalElements={}", devices.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events for a device",
            description = "Returns all login events for a specific device.")
    @GetMapping("/devices/{deviceId}/events")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getDeviceLoginEvents(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getDeviceLoginEvents - tenantId={}, deviceId={}", tenantId, deviceId);

        LoginAuditPageResponse response = loginAuditService.getDeviceLoginEvents(tenantId, deviceId, page, size);

        log.info("EXIT getDeviceLoginEvents - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get login events by device fingerprint",
            description = "Returns all login events for a device identified by fingerprint.")
    @GetMapping("/devices/fingerprint/{fingerprint}/events")
    public ResponseEntity<ResponseDto<LoginAuditPageResponse>> getDeviceLoginEventsByFingerprint(
            HttpServletRequest request,
            @Parameter(description = "Device fingerprint") @PathVariable String fingerprint,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getDeviceLoginEventsByFingerprint - tenantId={}, fingerprint={}", tenantId, fingerprint);

        LoginAuditPageResponse response = loginAuditService.getDeviceLoginEventsByFingerprint(tenantId, fingerprint, page, size);

        log.info("EXIT getDeviceLoginEventsByFingerprint - totalElements={}", response.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(response, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Search devices",
            description = "Search devices by name, OS, browser, or username.")
    @GetMapping("/devices/search")
    public ResponseEntity<ResponseDto<org.springframework.data.domain.Page<LoginDeviceDTO>>> searchDevices(
            HttpServletRequest request,
            @Parameter(description = "Search term") @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER searchDevices - tenantId={}, searchTerm={}", tenantId, q);

        var devices = loginAuditService.searchDevices(tenantId, q, page, size);

        log.info("EXIT searchDevices - totalElements={}", devices.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Trust a device",
            description = "Mark a device as trusted for a user.")
    @PostMapping("/devices/user/{userId}/trust")
    public ResponseEntity<ResponseDto<Map<String, Object>>> trustDevice(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Device fingerprint") @RequestParam String fingerprint
    ) {
        String tenantId = getTenantId(request);
        String trustedBy = jwtUtl.getPreferredUsernameFromRequest(request);
        log.info("ENTER trustDevice - tenantId={}, userId={}, fingerprint={}, trustedBy={}",
                tenantId, userId, fingerprint, trustedBy);

        boolean success = loginAuditService.trustDevice(tenantId, userId, fingerprint, trustedBy);

        Map<String, Object> result = Map.of(
                "success", success,
                "message", success ? "Device trusted successfully" : "Device not found"
        );

        log.info("EXIT trustDevice - success={}", success);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Block a device",
            description = "Block a device from logging in for a user.")
    @PostMapping("/devices/user/{userId}/block")
    public ResponseEntity<ResponseDto<Map<String, Object>>> blockDevice(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Device fingerprint") @RequestParam String fingerprint,
            @Parameter(description = "Block reason") @RequestParam(required = false) String reason
    ) {
        String tenantId = getTenantId(request);
        String blockedBy = jwtUtl.getPreferredUsernameFromRequest(request);
        log.info("ENTER blockDevice - tenantId={}, userId={}, fingerprint={}, blockedBy={}",
                tenantId, userId, fingerprint, blockedBy);

        boolean success = loginAuditService.blockDevice(tenantId, userId, fingerprint, blockedBy, reason);

        Map<String, Object> result = Map.of(
                "success", success,
                "message", success ? "Device blocked successfully" : "Device not found"
        );

        log.info("EXIT blockDevice - success={}", success);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Revoke all devices for a user",
            description = "Revoke access for all devices for a user (force re-login on all devices).")
    @PostMapping("/devices/user/{userId}/revoke-all")
    public ResponseEntity<ResponseDto<Map<String, Object>>> revokeAllUserDevices(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER revokeAllUserDevices - tenantId={}, userId={}", tenantId, userId);

        int revokedCount = loginAuditService.revokeAllUserDevices(tenantId, userId);

        Map<String, Object> result = Map.of(
                "revokedCount", revokedCount,
                "message", revokedCount + " devices revoked successfully"
        );

        log.info("EXIT revokeAllUserDevices - revokedCount={}", revokedCount);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get new device logins",
            description = "Returns logins from new devices within a time range.")
    @GetMapping("/devices/new-logins")
    public ResponseEntity<ResponseDto<org.springframework.data.domain.Page<LoginAuditEventDTO>>> getNewDeviceLogins(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getNewDeviceLogins - tenantId={}, start={}, end={}", tenantId, start, end);

        var events = loginAuditService.getNewDeviceLogins(tenantId, start, end, page, size);

        log.info("EXIT getNewDeviceLogins - totalElements={}", events.getTotalElements());
        return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get device login statistics",
            description = "Returns comprehensive device login statistics for the tenant.")
    @GetMapping("/devices/stats")
    public ResponseEntity<ResponseDto<DeviceLoginStatsDTO>> getDeviceLoginStats(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getDeviceLoginStats - tenantId={}, start={}, end={}", tenantId, start, end);

        DeviceLoginStatsDTO stats = loginAuditService.getDeviceLoginStats(tenantId, start, end);

        log.info("EXIT getDeviceLoginStats - totalDevices={}, totalLogins={}",
                stats.getTotalDevices(), stats.getTotalLogins());
        return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get devices with high login failures",
            description = "Returns devices with failed login attempts above the threshold (potential security issues).")
    @GetMapping("/devices/high-failures")
    public ResponseEntity<ResponseDto<List<LoginDeviceDTO>>> getDevicesWithHighFailures(
            HttpServletRequest request,
            @Parameter(description = "Failure threshold") @RequestParam(defaultValue = "5") int threshold
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getDevicesWithHighFailures - tenantId={}, threshold={}", tenantId, threshold);

        List<LoginDeviceDTO> devices = loginAuditService.getDevicesWithHighFailures(tenantId, threshold);

        log.info("EXIT getDevicesWithHighFailures - count={}", devices.size());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Get inactive devices",
            description = "Returns devices that haven't been used since the specified date.")
    @GetMapping("/devices/inactive")
    public ResponseEntity<ResponseDto<List<LoginDeviceDTO>>> getInactiveDevices(
            HttpServletRequest request,
            @Parameter(description = "Inactive since date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER getInactiveDevices - tenantId={}, since={}", tenantId, since);

        List<LoginDeviceDTO> devices = loginAuditService.getInactiveDevices(tenantId, since);

        log.info("EXIT getInactiveDevices - count={}", devices.size());
        return ResponseEntity.ok(new ResponseDto<>(devices, String.valueOf(HttpStatus.OK.value())));
    }

    @Operation(summary = "Count unique devices for a user",
            description = "Returns the count of unique devices a user has logged in from.")
    @GetMapping("/devices/user/{userId}/count")
    public ResponseEntity<ResponseDto<Map<String, Object>>> countUserDevices(
            HttpServletRequest request,
            @Parameter(description = "User ID") @PathVariable String userId
    ) {
        String tenantId = getTenantId(request);
        log.info("ENTER countUserDevices - tenantId={}, userId={}", tenantId, userId);

        long count = loginAuditService.countUniqueDevicesForUser(tenantId, userId);

        Map<String, Object> result = Map.of(
                "userId", userId,
                "deviceCount", count
        );

        log.info("EXIT countUserDevices - count={}", count);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }
}
