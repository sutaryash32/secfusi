package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.AuditLog;
import com.secufusion.tenant.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for querying audit log history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Get audit history for a specific entity.
     *
     * @param entityName The entity class name (e.g., "BrowserPolicy")
     * @param entityId   The entity's primary key
     * @return List of audit logs ordered by creation date descending
     */
    public List<AuditLog> getEntityHistory(String entityName, String entityId) {
        log.debug("Fetching audit history for entity {} with id {}", entityName, entityId);
        return auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId);
    }

    /**
     * Get all audit logs for a tenant with pagination.
     *
     * @param tenantId The tenant ID
     * @param page     Page number (0-based)
     * @param size     Page size
     * @return Page of audit logs
     */
    public Page<AuditLog> getAuditLogsByTenant(String tenantId, int page, int size) {
        log.debug("Fetching audit logs for tenant {} - page {} size {}", tenantId, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * Get all audit logs for a specific entity type.
     *
     * @param entityName The entity class name
     * @return List of audit logs
     */
    public List<AuditLog> getAuditLogsByEntityType(String entityName) {
        log.debug("Fetching audit logs for entity type {}", entityName);
        return auditLogRepository.findByEntityNameOrderByCreatedAtDesc(entityName);
    }

    /**
     * Get all audit logs for a specific entity type within a tenant.
     *
     * @param entityName The entity class name
     * @param tenantId   The tenant ID
     * @return List of audit logs
     */
    public List<AuditLog> getAuditLogsByEntityTypeAndTenant(String entityName, String tenantId) {
        log.debug("Fetching audit logs for entity type {} in tenant {}", entityName, tenantId);
        return auditLogRepository.findByEntityNameAndTenantIdOrderByCreatedAtDesc(entityName, tenantId);
    }

    /**
     * Get audit logs within a date range.
     *
     * @param startDate Start of the date range
     * @param endDate   End of the date range
     * @return List of audit logs
     */
    public List<AuditLog> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching audit logs between {} and {}", startDate, endDate);
        return auditLogRepository.findByDateRange(startDate, endDate);
    }

    /**
     * Get audit logs for a specific entity within a date range.
     *
     * @param entityName The entity class name
     * @param entityId   The entity's primary key
     * @param startDate  Start of the date range
     * @param endDate    End of the date range
     * @return List of audit logs
     */
    public List<AuditLog> getEntityHistoryByDateRange(String entityName, String entityId,
                                                      LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching audit history for entity {} id {} between {} and {}",
                entityName, entityId, startDate, endDate);
        return auditLogRepository.findByEntityAndDateRange(entityName, entityId, startDate, endDate);
    }

    /**
     * Get audit logs for a tenant within a date range with pagination.
     *
     * @param tenantId  The tenant ID
     * @param startDate Start of the date range
     * @param endDate   End of the date range
     * @param page      Page number (0-based)
     * @param size      Page size
     * @return Page of audit logs
     */
    public Page<AuditLog> getAuditLogsByTenantAndDateRange(String tenantId, LocalDateTime startDate,
                                                           LocalDateTime endDate, int page, int size) {
        log.debug("Fetching audit logs for tenant {} between {} and {} - page {} size {}",
                tenantId, startDate, endDate, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findByTenantIdAndDateRange(tenantId, startDate, endDate, pageable);
    }

    /**
     * Get audit logs by operation type (INSERT, UPDATE, DELETE).
     *
     * @param operation The operation type
     * @return List of audit logs
     */
    public List<AuditLog> getAuditLogsByOperation(String operation) {
        log.debug("Fetching audit logs for operation type {}", operation);
        return auditLogRepository.findByOperationOrderByCreatedAtDesc(operation);
    }

    /**
     * Get audit logs created by a specific user.
     *
     * @param username The username
     * @return List of audit logs
     */
    public List<AuditLog> getAuditLogsByUser(String username) {
        log.debug("Fetching audit logs created by user {}", username);
        return auditLogRepository.findByCreatedByOrderByCreatedAtDesc(username);
    }
}
