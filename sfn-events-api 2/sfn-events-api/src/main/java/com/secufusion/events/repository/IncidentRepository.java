package com.secufusion.events.repository;

import com.secufusion.events.entity.Incident;
import com.secufusion.events.entity.IncidentCategory;
import com.secufusion.events.entity.IncidentPriority;
import com.secufusion.events.entity.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    Optional<Incident> findByPkIncidentIdAndTenant_TenantID(String incidentId, String tenantId);

    // Paginated listing with optional filters
    @Query(value = """
            SELECT * FROM incidents i WHERE i.fk_tenant_id = :tenantId
            AND (CAST(:status AS VARCHAR) IS NULL OR i.status = CAST(:status AS VARCHAR))
            AND (CAST(:priority AS VARCHAR) IS NULL OR i.priority = CAST(:priority AS VARCHAR))
            AND (CAST(:category AS VARCHAR) IS NULL OR i.category = CAST(:category AS VARCHAR))
            AND (CAST(:assignedTo AS VARCHAR) IS NULL OR i.assigned_to = CAST(:assignedTo AS VARCHAR))
            ORDER BY i.created_at DESC
            """, nativeQuery = true)
    Page<Incident> findWithFilters(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("assignedTo") String assignedTo,
            Pageable pageable);

    // Count queries
    long countByTenant_TenantID(String tenantId);

    long countByTenant_TenantIDAndStatus(String tenantId, IncidentStatus status);

    long countByTenant_TenantIDAndPriority(String tenantId, IncidentPriority priority);

    @Query("SELECT i.status, COUNT(i) FROM Incident i WHERE i.tenant.tenantID = :tenantId GROUP BY i.status")
    List<Object[]> countByStatus(@Param("tenantId") String tenantId);

    @Query("SELECT i.priority, COUNT(i) FROM Incident i WHERE i.tenant.tenantID = :tenantId " +
            "AND i.status IN (com.secufusion.events.entity.IncidentStatus.OPEN, com.secufusion.events.entity.IncidentStatus.INVESTIGATING) " +
            "GROUP BY i.priority")
    List<Object[]> countOpenByPriority(@Param("tenantId") String tenantId);

    @Query("SELECT i.category, COUNT(i) FROM Incident i WHERE i.tenant.tenantID = :tenantId GROUP BY i.category")
    List<Object[]> countByCategory(@Param("tenantId") String tenantId);

    // Find open incidents by category (for auto-creation dedup)
    @Query("SELECT i FROM Incident i WHERE i.tenant.tenantID = :tenantId " +
            "AND i.category = :category " +
            "AND i.status IN (com.secufusion.events.entity.IncidentStatus.OPEN, com.secufusion.events.entity.IncidentStatus.INVESTIGATING)")
    List<Incident> findOpenByCategory(@Param("tenantId") String tenantId,
                                      @Param("category") IncidentCategory category);

    // Incident number sequence
    @Query(value = "SELECT nextval('incident_number_seq')", nativeQuery = true)
    Long getNextIncidentNumber();

    // MTTR (Mean Time To Resolve) in hours
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0)
            FROM incidents
            WHERE fk_tenant_id = :tenantId AND resolved_at IS NOT NULL
            """, nativeQuery = true)
    Double calculateMTTR(@Param("tenantId") String tenantId);

    // Recent incidents
    List<Incident> findTop10ByTenant_TenantIDOrderByCreatedAtDesc(String tenantId);

    /**
     * Count open/investigating incidents assigned to a specific user.
     * Used by auto-assign "least-loaded" algorithm.
     */
    @Query("SELECT COUNT(i) FROM Incident i WHERE i.tenant.tenantID = :tenantId " +
            "AND i.assignedTo = :userId " +
            "AND i.status IN (com.secufusion.events.entity.IncidentStatus.OPEN, " +
            "com.secufusion.events.entity.IncidentStatus.INVESTIGATING)")
    long countActiveIncidentsByAssignee(@Param("tenantId") String tenantId,
                                        @Param("userId") String userId);

    // Escalation candidates: open/investigating, not merged, not already escalated
    @Query(value = """
            SELECT * FROM incidents WHERE fk_tenant_id = :tenantId
            AND status IN ('OPEN','INVESTIGATING')
            AND (is_merged = FALSE OR is_merged IS NULL)
            AND escalated_at IS NULL
            AND (CAST(:priority AS VARCHAR) IS NULL OR priority = CAST(:priority AS VARCHAR))
            AND (
                (assigned_to IS NULL AND created_at < :unassignedThreshold)
                OR (resolved_at IS NULL AND created_at < :unresolvedThreshold)
            )
            """, nativeQuery = true)
    List<Incident> findEscalationCandidates(
            @Param("tenantId") String tenantId,
            @Param("priority") String priority,
            @Param("unassignedThreshold") Instant unassignedThreshold,
            @Param("unresolvedThreshold") Instant unresolvedThreshold);
}
