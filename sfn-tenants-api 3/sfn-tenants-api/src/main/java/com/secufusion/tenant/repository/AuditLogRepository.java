package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing audit log entries.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs for a specific entity type.
     */
    List<AuditLog> findByEntityNameOrderByCreatedAtDesc(String entityName);

    /**
     * Find all audit logs for a specific entity by name and ID.
     */
    List<AuditLog> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, String entityId);

    /**
     * Find all audit logs for a specific tenant.
     */
    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Find all audit logs for a specific tenant with pagination.
     */
    Page<AuditLog> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find all audit logs for a specific entity and tenant.
     */
    List<AuditLog> findByEntityNameAndTenantIdOrderByCreatedAtDesc(String entityName, String tenantId);

    /**
     * Find all audit logs by operation type.
     */
    List<AuditLog> findByOperationOrderByCreatedAtDesc(String operation);

    /**
     * Find all audit logs created by a specific user.
     */
    List<AuditLog> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Find all audit logs within a date range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find all audit logs for a specific entity within a date range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityName = :entityName AND a.entityId = :entityId " +
            "AND a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AuditLog> findByEntityAndDateRange(
            @Param("entityName") String entityName,
            @Param("entityId") String entityId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find all audit logs for a tenant within a date range with pagination.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId " +
            "AND a.createdAt BETWEEN :startDate AND :endDate")
    Page<AuditLog> findByTenantIdAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
