package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.LoggedInUserDetailsBean;
import com.secufusion.tenant.entity.AuditLog;
import com.secufusion.tenant.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for accessing audit logs.
 */
@RestController
@RequestMapping("/api/tenants/audit-logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Logs", description = "APIs for querying audit history")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Get audit logs for current tenant", description = "Returns paginated audit logs for the authenticated user's tenant")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LoggedInUserDetailsBean loggedInUser = (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.debug("Fetching audit logs for tenant {} - page {} size {}", tenantId, page, size);
        Page<AuditLog> auditLogs = auditLogService.getAuditLogsByTenant(tenantId, page, size);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/entity/{entityName}/{entityId}")
    @Operation(summary = "Get audit history for a specific entity", description = "Returns the complete audit history for a specific entity")
    public ResponseEntity<List<AuditLog>> getEntityHistory(
            @Parameter(description = "Entity class name (e.g., BrowserPolicy)") @PathVariable String entityName,
            @Parameter(description = "Entity ID") @PathVariable String entityId) {

        log.debug("Fetching audit history for entity {} with id {}", entityName, entityId);
        List<AuditLog> history = auditLogService.getEntityHistory(entityName, entityId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/entity-type/{entityName}")
    @Operation(summary = "Get audit logs by entity type", description = "Returns audit logs for a specific entity type within the current tenant")
    public ResponseEntity<List<AuditLog>> getAuditLogsByEntityType(
            HttpServletRequest request,
            @Parameter(description = "Entity class name (e.g., BrowserPolicy)") @PathVariable String entityName) {

        LoggedInUserDetailsBean loggedInUser = (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.debug("Fetching audit logs for entity type {} in tenant {}", entityName, tenantId);
        List<AuditLog> auditLogs = auditLogService.getAuditLogsByEntityTypeAndTenant(entityName, tenantId);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/date-range")
    @Operation(summary = "Get audit logs within a date range", description = "Returns paginated audit logs for the current tenant within the specified date range")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByDateRange(
            HttpServletRequest request,
            @Parameter(description = "Start date (ISO format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LoggedInUserDetailsBean loggedInUser = (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.debug("Fetching audit logs for tenant {} between {} and {}", tenantId, startDate, endDate);
        Page<AuditLog> auditLogs = auditLogService.getAuditLogsByTenantAndDateRange(tenantId, startDate, endDate, page, size);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/operation/{operation}")
    @Operation(summary = "Get audit logs by operation type", description = "Returns audit logs filtered by operation type (INSERT, UPDATE, DELETE)")
    public ResponseEntity<List<AuditLog>> getAuditLogsByOperation(
            @Parameter(description = "Operation type: INSERT, UPDATE, or DELETE") @PathVariable String operation) {

        log.debug("Fetching audit logs for operation type {}", operation);
        List<AuditLog> auditLogs = auditLogService.getAuditLogsByOperation(operation.toUpperCase());
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/user/{username}")
    @Operation(summary = "Get audit logs by user", description = "Returns audit logs created by a specific user")
    public ResponseEntity<List<AuditLog>> getAuditLogsByUser(
            @Parameter(description = "Username") @PathVariable String username) {

        log.debug("Fetching audit logs created by user {}", username);
        List<AuditLog> auditLogs = auditLogService.getAuditLogsByUser(username);
        return ResponseEntity.ok(auditLogs);
    }
}
