package com.secufusion.events.controller;

import com.secufusion.events.dto.ResponseDto;
import com.secufusion.events.dto.SecurityDashboardDTO;
import com.secufusion.events.dto.SecurityEventDTO;
import com.secufusion.events.dto.SecurityEventStatsDTO;
import com.secufusion.events.service.SecurityEventService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for security event tracking and analytics.
 */
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/events/security")
@Tag(name = "Security Events", description = "Security event tracking and analytics endpoints")
@RequiredArgsConstructor
public class SecurityEventController {

    private final SecurityEventService securityEventService;
    private final JwtUtl jwtUtl;

    /**
     * Get all security events with pagination.
     */
    @GetMapping
    @Operation(summary = "Get security events", description = "Returns paginated list of security events for the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEvents(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEvents - tenantId={} page={} size={}", tenantId, page, size);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEvents(tenantId, page, size);
            log.info("getSecurityEvents - success. tenantId={} totalElements={}", tenantId, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEvents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events with filters.
     */
    @GetMapping("/filter")
    @Operation(summary = "Get security events with filters",
               description = "Returns paginated list of security events matching the specified filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEventsWithFilters(
            HttpServletRequest request,
            @Parameter(description = "Severity level (critical, high, medium, low)") @RequestParam(required = false) String severity,
            @Parameter(description = "Threat type (csp_violation, malware, phishing, xss, etc.)") @RequestParam(required = false) String threatType,
            @Parameter(description = "Risk level (Critical, High, Medium, Low)") @RequestParam(required = false) String riskLevel,
            @Parameter(description = "Action taken (blocked, allowed, warned)") @RequestParam(required = false) String actionTaken,
            @Parameter(description = "Start datetime (ISO format)") @RequestParam LocalDateTime start,
            @Parameter(description = "End datetime (ISO format)") @RequestParam LocalDateTime end,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsWithFilters - tenantId={} severity={} threatType={} riskLevel={} actionTaken={}",
                tenantId, severity, threatType, riskLevel, actionTaken);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEventsWithFilters(
                    tenantId, severity, threatType, riskLevel, actionTaken, start, end, page, size);
            log.info("getSecurityEventsWithFilters - success. tenantId={} totalElements={}",
                    tenantId, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsWithFilters - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events by severity.
     */
    @GetMapping("/severity/{severity}")
    @Operation(summary = "Get security events by severity",
               description = "Returns paginated list of security events for a specific severity level")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEventsBySeverity(
            HttpServletRequest request,
            @Parameter(description = "Severity level (critical, high, medium, low)") @PathVariable String severity,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsBySeverity - tenantId={} severity={}", tenantId, severity);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEventsBySeverity(tenantId, severity, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsBySeverity - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events by threat type.
     */
    @GetMapping("/threat-type/{threatType}")
    @Operation(summary = "Get security events by threat type",
               description = "Returns paginated list of security events for a specific threat type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEventsByThreatType(
            HttpServletRequest request,
            @Parameter(description = "Threat type (csp_violation, malware, phishing, xss, etc.)") @PathVariable String threatType,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsByThreatType - tenantId={} threatType={}", tenantId, threatType);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEventsByThreatType(tenantId, threatType, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsByThreatType - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events by risk level.
     */
    @GetMapping("/risk-level/{riskLevel}")
    @Operation(summary = "Get security events by risk level",
               description = "Returns paginated list of security events for a specific risk level")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEventsByRiskLevel(
            HttpServletRequest request,
            @Parameter(description = "Risk level (Critical, High, Medium, Low)") @PathVariable String riskLevel,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsByRiskLevel - tenantId={} riskLevel={}", tenantId, riskLevel);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEventsByRiskLevel(tenantId, riskLevel, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsByRiskLevel - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events for a specific user.
     */
    @GetMapping("/user/{userName}")
    @Operation(summary = "Get security events by user",
               description = "Returns paginated list of security events for a specific user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEventsByUser(
            HttpServletRequest request,
            @Parameter(description = "Username") @PathVariable String userName,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsByUser - tenantId={} userName={}", tenantId, userName);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEventsByUser(tenantId, userName, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsByUser - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events for a specific device.
     */
    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get security events by device",
               description = "Returns paginated list of security events for a specific device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<SecurityEventDTO>>> getSecurityEventsByDevice(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsByDevice - tenantId={} deviceId={}", tenantId, deviceId);

        try {
            Page<SecurityEventDTO> events = securityEventService.getSecurityEventsByDevice(deviceId, page, size);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsByDevice - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events within a time range.
     */
    @GetMapping("/range")
    @Operation(summary = "Get security events by time range",
               description = "Returns security events within a specified time range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<SecurityEventDTO>>> getSecurityEventsByTimeRange(
            HttpServletRequest request,
            @Parameter(description = "Start datetime (ISO format)") @RequestParam LocalDateTime start,
            @Parameter(description = "End datetime (ISO format)") @RequestParam LocalDateTime end) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventsByTimeRange - tenantId={} start={} end={}", tenantId, start, end);

        try {
            List<SecurityEventDTO> events = securityEventService.getSecurityEventsByTimeRange(tenantId, start, end);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventsByTimeRange - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get critical security events.
     */
    @GetMapping("/critical")
    @Operation(summary = "Get critical security events",
               description = "Returns critical security events (severity=critical or riskLevel=Critical)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Critical security events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<SecurityEventDTO>>> getCriticalSecurityEvents(
            HttpServletRequest request,
            @Parameter(description = "Start datetime (ISO format)") @RequestParam LocalDateTime start,
            @Parameter(description = "End datetime (ISO format)") @RequestParam LocalDateTime end) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getCriticalSecurityEvents - tenantId={} start={} end={}", tenantId, start, end);

        try {
            List<SecurityEventDTO> events = securityEventService.getCriticalSecurityEvents(tenantId, start, end);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getCriticalSecurityEvents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security event statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get security event statistics",
               description = "Returns comprehensive security event statistics and analytics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<SecurityEventStatsDTO>> getSecurityEventStats(
            HttpServletRequest request,
            @Parameter(description = "Start datetime (ISO format)") @RequestParam LocalDateTime start,
            @Parameter(description = "End datetime (ISO format)") @RequestParam LocalDateTime end) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventStats - tenantId={} start={} end={}", tenantId, start, end);

        try {
            SecurityEventStatsDTO stats = securityEventService.getSecurityEventStats(tenantId, start, end);
            return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventStats - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get count of security events.
     */
    @GetMapping("/count")
    @Operation(summary = "Get security event count", description = "Returns total count of security events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Long>> getSecurityEventCount(
            HttpServletRequest request,
            @Parameter(description = "Start datetime (optional)") @RequestParam(required = false) LocalDateTime start,
            @Parameter(description = "End datetime (optional)") @RequestParam(required = false) LocalDateTime end) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityEventCount - tenantId={} start={} end={}", tenantId, start, end);

        try {
            long count;
            if (start != null && end != null) {
                count = securityEventService.countSecurityEventsByTimeRange(tenantId, start, end);
            } else {
                count = securityEventService.countSecurityEvents(tenantId);
            }
            return ResponseEntity.ok(new ResponseDto<>(count, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityEventCount - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get security events dashboard data for UI.
     * Returns summary stats, event type breakdown, and recent events.
     * Requires date range filtering with start and end dates.
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Get security dashboard data",
               description = "Returns dashboard data including summary stats, event type breakdown, and recent events. " +
                       "Requires 'start' and 'end' date parameters for filtering.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<SecurityDashboardDTO>> getSecurityDashboard(
            HttpServletRequest request,
            @Parameter(description = "Start date (ISO format: 2026-01-01T00:00:00)", required = true) @RequestParam LocalDateTime start,
            @Parameter(description = "End date (ISO format: 2026-01-26T23:59:59)", required = true) @RequestParam LocalDateTime end,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Event type filter: SECURITY_THREAT, POLICY_VIOLATION, DLP_ALERT, COMPLIANCE") @RequestParam(required = false) String eventType,
            @Parameter(description = "Search term (optional)") @RequestParam(required = false) String search) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getSecurityDashboard - tenantId={} start={} end={} page={} size={} eventType={} search={}",
                tenantId, start, end, page, size, eventType, search);

        // Validate date range
        if (start.isAfter(end)) {
            log.warn("getSecurityDashboard - start is after end");
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "'start' must be before 'end'"));
        }

        try {
            SecurityDashboardDTO dashboard = securityEventService.getSecurityDashboard(
                    tenantId, start, end, page, size, eventType, search);
            log.info("getSecurityDashboard - success. tenantId={} totalEvents={}",
                    tenantId, dashboard.getSummary().getTotalEvents());
            return ResponseEntity.ok(new ResponseDto<>(dashboard, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getSecurityDashboard - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get event details by ID for modal view.
     */
    @GetMapping("/detail/{eventId}")
    @Operation(summary = "Get event details",
               description = "Returns detailed information about a specific security event for the Event Details modal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<SecurityDashboardDTO.EventDetailDTO>> getEventDetail(
            HttpServletRequest request,
            @Parameter(description = "Event ID") @PathVariable String eventId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getEventDetail - tenantId={} eventId={}", tenantId, eventId);

        try {
            SecurityDashboardDTO.EventDetailDTO detail = securityEventService.getEventDetail(tenantId, eventId);
            if (detail == null) {
                log.warn("getEventDetail - event not found. tenantId={} eventId={}", tenantId, eventId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto<>(null, "Event not found"));
            }
            log.info("getEventDetail - success. tenantId={} eventId={}", tenantId, eventId);
            return ResponseEntity.ok(new ResponseDto<>(detail, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getEventDetail - error. tenantId={} eventId={}", tenantId, eventId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get filter options for dashboard.
     */
    @GetMapping("/filters")
    @Operation(summary = "Get filter options",
               description = "Returns available filter options for the Security Events Dashboard")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filter options retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<SecurityDashboardDTO.FilterOptions>> getFilterOptions(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getFilterOptions - tenantId={}", tenantId);

        try {
            SecurityDashboardDTO.FilterOptions filters = securityEventService.getFilterOptions(tenantId);
            log.info("getFilterOptions - success. tenantId={}", tenantId);
            return ResponseEntity.ok(new ResponseDto<>(filters, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getFilterOptions - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

}
