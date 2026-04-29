package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.service.IncidentService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/events/incidents")
@Tag(name = "Incidents", description = "Incident management endpoints for security event case tracking")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final JwtUtl jwtUtl;

    // ==================== CREATE ====================

    @PostMapping
    @Operation(summary = "Create incident", description = "Manually create a new incident with optional event linking")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Incident created successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> createIncident(
            HttpServletRequest request,
            @Valid @RequestBody CreateIncidentRequest createRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("createIncident - tenantId={} userId={} title={}", tenantId, userId, createRequest.getTitle());

        try {
            IncidentDTO incident = incidentService.createIncident(tenantId, userId, userName, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(incident, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("createIncident - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== LIST ====================

    @GetMapping
    @Operation(summary = "List incidents", description = "Returns paginated list of incidents with optional filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incidents retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<IncidentDTO>>> getIncidents(
            HttpServletRequest request,
            @Parameter(description = "Filter by status (OPEN, INVESTIGATING, RESOLVED, CLOSED, FALSE_POSITIVE)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by priority (P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW)")
            @RequestParam(required = false) String priority,
            @Parameter(description = "Filter by category (MALWARE, PHISHING, DATA_LEAK, etc.)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filter by assigned user ID")
            @RequestParam(required = false) String assignedTo,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getIncidents - tenantId={} status={} priority={} category={}", tenantId, status, priority, category);

        try {
            Page<IncidentDTO> incidents = incidentService.getIncidents(
                    tenantId, status, priority, category, assignedTo, page, size);
            log.info("getIncidents - success. tenantId={} totalElements={}", tenantId, incidents.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(incidents, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getIncidents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== STATS & DASHBOARD (before {id} paths) ====================

    @GetMapping("/stats")
    @Operation(summary = "Get incident statistics", description = "Returns incident statistics including counts by status, priority, category, and MTTR")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentStatsDTO>> getStats(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getStats - tenantId={}", tenantId);

        try {
            IncidentStatsDTO stats = incidentService.getStats(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(stats, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getStats - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get incident dashboard", description = "Returns dashboard data with stats and recent incidents")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDashboardDTO>> getDashboard(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getDashboard - tenantId={}", tenantId);

        try {
            IncidentDashboardDTO dashboard = incidentService.getDashboard(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(dashboard, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getDashboard - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/pending-events")
    @Operation(summary = "Get pending event counts", description = "Returns count of pending security events eligible for incident creation. Use before auto-create or bulk-dismiss to understand scope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Counts retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Long>>> getPendingEventCounts(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getPendingEventCounts - tenantId={}", tenantId);

        try {
            Map<String, Long> counts = incidentService.getPendingEventCounts(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(counts, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getPendingEventCounts - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PostMapping("/bulk-dismiss")
    @Operation(summary = "Bulk dismiss old events", description = "Marks old pending security events as 'Reviewed' before a cutoff date so they won't trigger incidents. Use after first deployment to skip historical events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events dismissed successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Map<String, Object>>> bulkDismissOldEvents(
            HttpServletRequest request,
            @Parameter(description = "Cutoff date — events before this are dismissed (ISO format: 2026-02-01T00:00:00)")
            @RequestParam String cutoffDate) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("bulkDismissOldEvents - tenantId={} cutoffDate={}", tenantId, cutoffDate);

        try {
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.parse(cutoffDate);
            int dismissed = incidentService.bulkDismissOldEvents(tenantId, userId, userName, cutoff);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("eventsDismissed", dismissed);
            result.put("cutoffDate", cutoffDate);
            result.put("message", dismissed + " old pending events marked as Reviewed");

            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("bulkDismissOldEvents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== BULK OPERATIONS ====================

    @PostMapping("/bulk-assign")
    @Operation(summary = "Bulk assign incidents", description = "Assign multiple incidents to a single analyst")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk assign completed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<BulkOperationResult>> bulkAssign(
            HttpServletRequest request,
            @Valid @RequestBody BulkAssignRequest bulkRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            BulkOperationResult result = incidentService.bulkAssign(tenantId, userId, userName, bulkRequest);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("bulkAssign - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PostMapping("/bulk-status")
    @Operation(summary = "Bulk status change", description = "Change status of multiple incidents at once")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk status change completed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<BulkOperationResult>> bulkStatusChange(
            HttpServletRequest request,
            @Valid @RequestBody BulkStatusRequest bulkRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            BulkOperationResult result = incidentService.bulkStatusChange(tenantId, userId, userName, bulkRequest);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("bulkStatusChange - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PostMapping("/bulk-close")
    @Operation(summary = "Bulk close incidents", description = "Close multiple incidents at once with optional resolution notes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk close completed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<BulkOperationResult>> bulkClose(
            HttpServletRequest request,
            @Valid @RequestBody BulkCloseRequest bulkRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            BulkOperationResult result = incidentService.bulkClose(tenantId, userId, userName, bulkRequest);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("bulkClose - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== ASSIGNEE CONFIGURATION ====================

    @PostMapping("/assignees")
    @Operation(summary = "Add assignee config",
            description = "Configure an analyst to handle incidents of a specific category. Set category=null for 'all categories' fallback.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assignee config created"),
            @ApiResponse(responseCode = "409", description = "Duplicate assignee config"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentAssigneeDTO>> addAssigneeConfig(
            HttpServletRequest request,
            @Valid @RequestBody CreateIncidentAssigneeRequest createRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            IncidentAssigneeDTO result = incidentService.addAssigneeConfig(
                    tenantId, userId, userName, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(result, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("addAssigneeConfig - error. tenantId={}", tenantId, ex);
            HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PostMapping("/assignees/bulk-init")
    @Operation(summary = "Bulk initialize assignee configs",
            description = "Create multiple assignee configs at once. Skips duplicates (same user + category). " +
                    "Useful for initial setup when onboarding a team of analysts.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assignee configs created (duplicates skipped)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<IncidentAssigneeDTO>>> bulkInitAssigneeConfigs(
            HttpServletRequest request,
            @Valid @RequestBody List<CreateIncidentAssigneeRequest> assignees) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("bulkInitAssigneeConfigs - tenantId={} count={}", tenantId, assignees.size());

        try {
            List<IncidentAssigneeDTO> created = incidentService.bulkInitAssigneeConfigs(tenantId, assignees);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(created, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("bulkInitAssigneeConfigs - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/assignees")
    @Operation(summary = "List assignee configs",
            description = "Get all assignee configuration entries for the tenant, including current active incident load per analyst.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignee configs retrieved"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<IncidentAssigneeDTO>>> listAssigneeConfigs(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            List<IncidentAssigneeDTO> assignees = incidentService.listAssigneeConfigs(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(assignees, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("listAssigneeConfigs - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PutMapping("/assignees/{assigneeId}")
    @Operation(summary = "Update assignee config",
            description = "Update isActive, assignmentOrder, or userName for an assignee config")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignee config updated"),
            @ApiResponse(responseCode = "404", description = "Assignee config not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentAssigneeDTO>> updateAssigneeConfig(
            HttpServletRequest request,
            @PathVariable String assigneeId,
            @RequestBody Map<String, Object> body) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            Boolean isActive = body.containsKey("isActive") ? (Boolean) body.get("isActive") : null;
            Integer assignmentOrder = body.containsKey("assignmentOrder")
                    ? ((Number) body.get("assignmentOrder")).intValue() : null;
            String userName = body.containsKey("userName") ? (String) body.get("userName") : null;

            IncidentAssigneeDTO result = incidentService.updateAssigneeConfig(
                    tenantId, assigneeId, isActive, assignmentOrder, userName);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("updateAssigneeConfig - error. tenantId={} assigneeId={}", tenantId, assigneeId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @DeleteMapping("/assignees/{assigneeId}")
    @Operation(summary = "Remove assignee config",
            description = "Delete an assignee configuration entry. Does not affect existing incident assignments.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignee config removed"),
            @ApiResponse(responseCode = "404", description = "Assignee config not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<String>> removeAssigneeConfig(
            HttpServletRequest request,
            @PathVariable String assigneeId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            incidentService.removeAssigneeConfig(tenantId, assigneeId);
            return ResponseEntity.ok(new ResponseDto<>("Assignee config removed successfully",
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("removeAssigneeConfig - error. tenantId={} assigneeId={}", tenantId, assigneeId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== AUTO-CREATE ====================

    @PostMapping("/auto-create")
    @Operation(summary = "Trigger auto-creation", description = "Scans for pending high-severity security events and auto-creates incidents. Auto-assigns to configured analysts using least-loaded logic.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auto-creation completed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<IncidentDTO>>> triggerAutoCreate(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("triggerAutoCreate - tenantId={}", tenantId);

        try {
            List<IncidentDTO> incidents = incidentService.autoCreateIncidents(tenantId, userId, userName);
            log.info("triggerAutoCreate - success. tenantId={} incidentsCreated={}", tenantId, incidents.size());
            return ResponseEntity.ok(new ResponseDto<>(incidents, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("triggerAutoCreate - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== MERGE ====================

    @PostMapping("/{parentId}/merge")
    @Operation(summary = "Merge incidents", description = "Merge child incidents into a parent. Events are transferred, child status becomes MERGED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incidents merged successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid merge request"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> mergeIncidents(
            HttpServletRequest request,
            @PathVariable String parentId,
            @Valid @RequestBody MergeIncidentsRequest mergeRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            IncidentDTO result = incidentService.mergeIncidents(tenantId, userId, userName, parentId, mergeRequest);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, ex.getMessage()));
        } catch (Exception ex) {
            log.error("mergeIncidents - error. tenantId={} parentId={}", tenantId, parentId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== DETAIL ====================

    @GetMapping("/{id}")
    @Operation(summary = "Get incident detail", description = "Returns full incident details with linked events and activity timeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDetailDTO>> getIncidentDetail(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getIncidentDetail - tenantId={} incidentId={}", tenantId, id);

        try {
            IncidentDetailDTO detail = incidentService.getIncidentDetail(tenantId, id);
            return ResponseEntity.ok(new ResponseDto<>(detail, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getIncidentDetail - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== UPDATE ====================

    @PutMapping("/{id}")
    @Operation(summary = "Update incident", description = "Update incident title, description, priority, or category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident updated successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> updateIncident(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody UpdateIncidentRequest updateRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("updateIncident - tenantId={} incidentId={}", tenantId, id);

        try {
            IncidentDTO incident = incidentService.updateIncident(tenantId, userId, userName, id, updateRequest);
            return ResponseEntity.ok(new ResponseDto<>(incident, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("updateIncident - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== STATUS ====================

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change incident status", description = "Change status with state machine validation (OPEN -> INVESTIGATING -> RESOLVED -> CLOSED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> changeStatus(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Parameter(description = "New status") @RequestParam String status) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("changeStatus - tenantId={} incidentId={} newStatus={}", tenantId, id, status);

        try {
            IncidentDTO incident = incidentService.changeStatus(tenantId, userId, userName, id, status);
            return ResponseEntity.ok(new ResponseDto<>(incident, String.valueOf(HttpStatus.OK.value())));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("changeStatus - bad request. tenantId={} incidentId={} error={}",
                    tenantId, id, ex.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, ex.getMessage()));
        } catch (Exception ex) {
            log.error("changeStatus - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== RESOLVE ====================

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve incident", description = "Resolve incident with resolution notes and root cause. Sets all linked events to 'Reviewed'")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident resolved successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> resolveIncident(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody ResolveIncidentRequest resolveRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("resolveIncident - tenantId={} incidentId={}", tenantId, id);

        try {
            IncidentDTO incident = incidentService.resolveIncident(tenantId, userId, userName, id, resolveRequest);
            return ResponseEntity.ok(new ResponseDto<>(incident, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("resolveIncident - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== ASSIGN ====================

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign incident", description = "Assign incident to a user. Auto-moves status to INVESTIGATING if currently OPEN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident assigned successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> assignIncident(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody AssignIncidentRequest assignRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("assignIncident - tenantId={} incidentId={} assignedTo={}", tenantId, id, assignRequest.getAssignedTo());

        try {
            IncidentDTO incident = incidentService.assignIncident(tenantId, userId, userName, id, assignRequest);
            return ResponseEntity.ok(new ResponseDto<>(incident, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("assignIncident - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== PRIORITY ====================

    @PatchMapping("/{id}/priority")
    @Operation(summary = "Change priority", description = "Change incident priority")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Priority changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid priority value"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> changePriority(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Parameter(description = "New priority") @RequestParam String priority) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("changePriority - tenantId={} incidentId={} newPriority={}", tenantId, id, priority);

        try {
            IncidentDTO incident = incidentService.changePriority(tenantId, userId, userName, id, priority);
            return ResponseEntity.ok(new ResponseDto<>(incident, String.valueOf(HttpStatus.OK.value())));
        } catch (IllegalArgumentException ex) {
            log.warn("changePriority - bad request. tenantId={} incidentId={} error={}",
                    tenantId, id, ex.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, ex.getMessage()));
        } catch (Exception ex) {
            log.error("changePriority - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== EVENT LINKING ====================

    @PostMapping("/{id}/events")
    @Operation(summary = "Link events", description = "Link security events to this incident. Sets linked events processingStatus to 'Processed'")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events linked successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentDTO>> linkEvents(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody List<String> eventIds) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("linkEvents - tenantId={} incidentId={} eventCount={}", tenantId, id, eventIds.size());

        try {
            IncidentDTO incident = incidentService.linkEvents(tenantId, userId, userName, id, eventIds);
            return ResponseEntity.ok(new ResponseDto<>(incident, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("linkEvents - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}/events/{eventId}")
    @Operation(summary = "Unlink event", description = "Remove an event from this incident. Reverts event processingStatus to 'Pending' if not linked elsewhere")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event unlinked successfully"),
            @ApiResponse(responseCode = "404", description = "Incident or event link not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<String>> unlinkEvent(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Parameter(description = "Event ID") @PathVariable String eventId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        log.info("unlinkEvent - tenantId={} incidentId={} eventId={}", tenantId, id, eventId);

        try {
            incidentService.unlinkEvent(tenantId, userId, userName, id, eventId);
            return ResponseEntity.ok(new ResponseDto<>("Event unlinked successfully",
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("unlinkEvent - error. tenantId={} incidentId={} eventId={}", tenantId, id, eventId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Get linked events", description = "Returns all security events linked to this incident")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Linked events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<SecurityEventDTO>>> getLinkedEvents(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getLinkedEvents - tenantId={} incidentId={}", tenantId, id);

        try {
            List<SecurityEventDTO> events = incidentService.getLinkedEvents(tenantId, id);
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getLinkedEvents - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== TIMELINE ====================

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get activity timeline", description = "Returns paginated activity timeline for this incident")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<IncidentActivityDTO>>> getTimeline(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getTimeline - tenantId={} incidentId={} page={} size={}", tenantId, id, page, size);

        try {
            Page<IncidentActivityDTO> timeline = incidentService.getTimeline(tenantId, id, page, size);
            return ResponseEntity.ok(new ResponseDto<>(timeline, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getTimeline - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== COMMENTS ====================

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add comment", description = "Add a comment to the incident timeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment added successfully"),
            @ApiResponse(responseCode = "404", description = "Incident not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<IncidentActivityDTO>> addComment(
            HttpServletRequest request,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);
        String comment = body.get("comment");
        log.info("addComment - tenantId={} incidentId={}", tenantId, id);

        if (comment == null || comment.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "Comment is required"));
        }

        try {
            IncidentActivityDTO activity = incidentService.addComment(tenantId, userId, userName, id, comment);
            return ResponseEntity.ok(new ResponseDto<>(activity, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("addComment - error. tenantId={} incidentId={}", tenantId, id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }
}
