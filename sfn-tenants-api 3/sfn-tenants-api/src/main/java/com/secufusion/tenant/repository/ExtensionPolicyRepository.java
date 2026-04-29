package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.ExtensionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExtensionPolicyRepository
        extends JpaRepository<ExtensionPolicy, String> {

    Optional<ExtensionPolicy> findTopByFkTenantIdIsNull();

    Optional<ExtensionPolicy>
    findTopByFkTenantIdAndIsActiveTrueOrderByVersionDesc(String tenantId);

    Optional<ExtensionPolicy> findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(String tenantId);

    // Dashboard statistics methods
    long countByFkTenantId(String tenantId);
    long countByFkTenantIdAndIsActive(String tenantId, boolean isActive);
    long countByIsActive(boolean isActive);

    /**
     * Find all active extension policies for tenant with all related entities eagerly loaded.
     * Uses JOIN FETCH to avoid N+1 queries and LazyInitializationException.
     *
     * Eagerly loads:
     * - urlFilters (OneToMany - LAZY)
     * - dlp (OneToOne)
     * - managedExtension (OneToOne)
     * - complianceRules (OneToOne)
     */
    @Query("SELECT DISTINCT e FROM ExtensionPolicy e " +
            "LEFT JOIN FETCH e.urlFilters " +
            "LEFT JOIN FETCH e.dlp " +
            "LEFT JOIN FETCH e.managedExtension " +
            "LEFT JOIN FETCH e.complianceRules " +
            "WHERE e.fkTenantId = :tenantId " +
            "AND e.isActive = true " +
            "ORDER BY e.createdAt DESC")
    List<ExtensionPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(@Param("tenantId") String tenantId);

    /**
     * Find extension policy by ID with all related entities eagerly loaded.
     * Uses JOIN FETCH to avoid LazyInitializationException.
     */
    @Query("SELECT e FROM ExtensionPolicy e " +
            "LEFT JOIN FETCH e.urlFilters " +
            "LEFT JOIN FETCH e.dlp " +
            "LEFT JOIN FETCH e.managedExtension " +
            "LEFT JOIN FETCH e.complianceRules " +
            "WHERE e.pkExtensionPolicyId = :id")
    Optional<ExtensionPolicy> findByIdWithRelations(@Param("id") String id);
}
