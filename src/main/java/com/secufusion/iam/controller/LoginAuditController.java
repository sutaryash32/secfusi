package com.secufusion.iam.controller;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.iam.entity.LoginAuditEvent.SourceService;
import com.secufusion.iam.entity.Tenant;
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
}
